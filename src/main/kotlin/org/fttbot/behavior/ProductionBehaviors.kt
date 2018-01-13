package org.fttbot.behavior

import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.ConstructionPosition
import org.fttbot.FTTBot
import org.fttbot.FTTConfig
import org.fttbot.behavior.ProductionBoard.orderedConstructions
import org.fttbot.board
import org.fttbot.decision.StrategyUF
import org.fttbot.info.*
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*
import java.util.logging.Logger

class TrainOrAddon : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        if (unit !is TrainingFacility) return Status.FAILED
        if (unit.isTraining || unit.trainingQueueSize > 0) return Status.RUNNING
        if (ProductionBoard.queue.isEmpty()) return Status.SUCCEEDED
        val item = ProductionBoard.queue.peek()
        if (item !is ProductionBoard.UnitItem || !item.canAfford || !item.canProcess) return Status.FAILED
        if (!unit.canTrain(item.type)) return Status.FAILED
        if (!unit.train(item.type) && (unit !is ExtendibleByAddon || !unit.build(item.type))) {
            if (!item.type.isAddon || unit !is ExtendibleByAddon || unit.addon == null) {
                LOG.severe("${unit} couldn't start training/building ${item.type}")
            }
            return Status.FAILED
        }
        LOG.info("${unit} started training ${item.type}")
        ProductionBoard.queue.pop()
        return Status.RUNNING
    }
}

class ResearchOrUpgrade : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        if (unit !is ResearchingFacility) return Status.FAILED
        if (unit.isResearching || unit.isUpgrading) return Status.RUNNING
        if (ProductionBoard.queue.isEmpty()) return Status.SUCCEEDED
        val item = ProductionBoard.queue.peek()
        if (!item.canAfford || !item.canProcess) return Status.FAILED
        if (when (item) {
            is ProductionBoard.TechItem -> if (unit.canResearch(item.type)) {
                if (!unit.research(item.type)) {
                    LOG.severe("${unit} couldn't start researching ${item.type}")
                    false
                } else true
            } else false
            is ProductionBoard.UpgradeItem -> if (unit.canUpgrade(item.type)) {
                if (!unit.upgrade(item.type)) {
                    LOG.severe("${unit} couldn't start upgrading ${item.type}")
                    false
                } else true
            } else false
            else -> false
        }) {
            ProductionBoard.queue.pop()
            return Status.RUNNING
        }
        return Status.FAILED
    }
}

class WorkerProduction : LeafTask<ProductionBoard>() {
    override fun execute(): Status {
        UnitQuery.myBases.forEach { base ->
            val units = UnitQuery.unitsInRadius(base.position, 300)
            val workerDelta = units.map { if (it is MineralPatch) 2 else if (it is GasMiningFacility) 3 else if (it is MobileUnit && it.isWorker && it.isMyUnit) -1 else 0 }.sum()
            if (workerDelta > 0 && !board().queue.any { (it as? ProductionBoard.UnitItem)?.type?.isWorker == true && it.favoriteProducer == base }) {
                board().queue.add(ProductionBoard.UnitItem(FTTConfig.WORKER, favoriteBuilder = base))
                return Status.SUCCEEDED
            }
        }
        return Status.FAILED
    }

    override fun copyTo(task: Task<ProductionBoard>): Task<ProductionBoard> = task
}

class SupplyProduction : LeafTask<ProductionBoard>() {
    override fun execute(): Status {
        return if (org.fttbot.decision.Construction.supplyNeeded() > 0.7) {
            board().queue.add(ProductionBoard.UnitItem(FTTConfig.SUPPLY))
            Status.SUCCEEDED
        } else
            Status.FAILED
    }

    override fun copyTo(task: Task<ProductionBoard>): Task<ProductionBoard> = task
}

class DetectorProduction : LeafTask<ProductionBoard>() {
    override fun execute(): Status {
        return if (StrategyUF.needMobileDetection() > 0.7 && board().queue.none { it is ProductionBoard.UnitItem && it.type.isDetector }) {
            val toBuild = PlayerUnit.getMissingUnits(UnitQuery.myUnits, UnitType.Terran_Comsat_Station.allRequiredUnits())
                    .firstOrNull { ut ->
                        FTTBot.self.canMake(ut)
                                && board().orderedConstructions.none { it.type == ut }
                                && board().queue.none { it is ProductionBoard.UnitItem && it.type == ut }
                    } ?: return Status.FAILED
            board().queue.offer(ProductionBoard.UnitItem(toBuild))
            Status.SUCCEEDED
        } else
            Status.FAILED
    }

    override fun copyTo(task: Task<ProductionBoard>): Task<ProductionBoard> = task
}

class ProduceAttacker : LeafTask<ProductionBoard>() {
    override fun execute(): Status {
        if (!board().queue.isEmpty()) return Status.FAILED
        val added = UnitQuery.myUnits.filter { it is TrainingFacility && !it.isTraining && it.trainingQueueSize == 0 }.map { (it as TrainingFacility).trains() }
                .flatten()
                .filter { it.canAttack() && !it.isWorker && it.canMove() && FTTBot.self.canMake(it) }
                .groupBy { it }
                .map { (ut, l) -> ut to l.size }
                .shuffled()
                .sumBy { (type, count) ->
                    val todo = count - board().queue.count { (it as? ProductionBoard.UnitItem)?.type == type }
                    repeat(todo) { board().queue.add(ProductionBoard.UnitItem(type)) }
                    todo
                }
        if (added > 0) return Status.SUCCEEDED
        return Status.FAILED
    }

    override fun copyTo(task: Task<ProductionBoard>): Task<ProductionBoard> = task

}

class BuildNextItemFromProductionQueue : LeafTask<ProductionBoard>() {
    private val LOG = Logger.getLogger(this::class.java.simpleName)

    override fun execute(): Status {
        val queue = board().queue

        orderedConstructions.removeIf { it.building?.isCompleted ?: false || it.started && FTTConfig.MY_RACE != Race.Terran }
        val abortedConstructions = orderedConstructions.minus(UnitQuery.myWorkers.filter { it.board.construction != null }.map { it.board.construction!! })
        abortedConstructions.forEach { continueConstruction(it) }

        while (!queue.isEmpty() && queue.peek().canAfford) {
            val peekedItem = queue.peek()
            if (!peekedItem.canProcess) {
                if ((peekedItem as? ProductionBoard.UnitItem)?.type?.supplyRequired() ?: 0 > 0 && orderedConstructions.any { it.type.supplyProvided() > 0 }) {
                    LOG.finest("Can't build ${peekedItem}.type} yet, but supply is being constructed")
                    break
                } else if (orderedConstructions.any { it.type == peekedItem.whatProduces }) {
                    LOG.finest("Can't build ${peekedItem} yet, but dependency is being built")
                    break
                } else if (peekedItem is ProductionBoard.UnitItem && peekedItem.type.isAddon &&
                        orderedConstructions.any { it.type == peekedItem.type.whatBuilds().first }) {
                    LOG.finest("Can't build ${peekedItem} yet, but dependency is being built")
                    break
                } else {
                    val item = queue.pop()
                    LOG.severe("Skipping over build item ${item}, can't build it")
                }
                continue
            }
            val toBuild = queue.peek()

            val builderType = toBuild.whatProduces
            if (builderType.isWorker) {
                if (!construct(toBuild as ProductionBoard.UnitItem)) break
            } else break
            with(board()) {
                reservedGas += toBuild.type.gasPrice()
                reservedMinerals += toBuild.type.mineralPrice()
            }
            queue.pop()
        }

        return if (queue.isEmpty()) Status.FAILED else Status.RUNNING
    }

    private fun continueConstruction(construction: Construction) {
        if (!construction.started) {
            construction.position = ConstructionPosition.findPositionFor(construction.type) ?: return
        }
        val worker = findWorker(construction.position.toPosition()) ?: return
        worker.board.construction = construction
    }

    private fun construct(toBuild: ProductionBoard.UnitItem): Boolean {
        val position = ConstructionPosition.findPositionFor(toBuild.type) ?: return false
        val worker = toBuild.favoriteProducer as? Worker<*> ?: findWorker(position.toPosition()) ?: return false
        worker.board.construction = Construction(toBuild.type, position)
        orderedConstructions.offer(worker.board.construction)
        return true
    }

    override fun copyTo(task: Task<ProductionBoard>?): Task<ProductionBoard> = BuildNextItemFromProductionQueue()
}