package org.fttbot.behavior

import org.fttbot.*
import org.fttbot.behavior.ProductionBoard.orderedConstructions
import org.fttbot.decision.StrategyUF
import org.fttbot.info.*
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*
import java.util.logging.Logger

class TrainOrAddon : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit
        if (unit !is TrainingFacility) return NodeStatus.FAILED
        if (unit.isTraining || unit.trainingQueueSize > 0) return NodeStatus.RUNNING
        if (ProductionBoard.queue.isEmpty()) return NodeStatus.SUCCEEDED
        val item = ProductionBoard.queue.peek()
        if (item !is ProductionBoard.UnitItem || !item.canAfford || !item.canProcess) return NodeStatus.FAILED
        if (!unit.canTrain(item.type)) return NodeStatus.FAILED
        if (!unit.train(item.type) && (unit !is ExtendibleByAddon || !unit.build(item.type))) {
            if (!item.type.isAddon || unit !is ExtendibleByAddon || unit.addon == null) {
                LOG.severe("${unit} couldn't start training/building ${item.type}")
            }
            return NodeStatus.FAILED
        }
        LOG.info("${unit} started training ${item.type}")
        ProductionBoard.queue.pop() as ProductionBoard.UnitItem
        return NodeStatus.RUNNING
    }
}

class ResearchOrUpgrade : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit
        if (unit !is ResearchingFacility) return NodeStatus.FAILED
        if (unit.isResearching || unit.isUpgrading) return NodeStatus.RUNNING
        if (ProductionBoard.queue.isEmpty()) return NodeStatus.SUCCEEDED
        val item = ProductionBoard.queue.peek()
        if (!item.canAfford || !item.canProcess) return NodeStatus.FAILED
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
            return NodeStatus.RUNNING
        }
        return NodeStatus.FAILED
    }
}

class WorkerProduction : Node<ProductionBoard> {
    override fun tick(board: ProductionBoard): NodeStatus {
        UnitQuery.myBases.forEach { base ->
            val units = UnitQuery.unitsInRadius(base.position, 300)
            val workerDelta = units.map { if (it is MineralPatch) 2 else if (it is GasMiningFacility) 3 else if (it is MobileUnit && it.isWorker && it.isMyUnit) -1 else 0 }.sum()
            if (workerDelta > 0 && !board.queue.any { (it as? ProductionBoard.UnitItem)?.type?.isWorker == true && it.favoriteProducer == base }) {
                board.queue.add(ProductionBoard.UnitItem(FTTConfig.WORKER, favoriteBuilder = base))
                return NodeStatus.SUCCEEDED
            }
        }
        return NodeStatus.FAILED
    }
}

class SupplyProduction : Node<ProductionBoard> {
    override fun tick(board: ProductionBoard): NodeStatus {
        return if (org.fttbot.decision.Construction.supplyNeeded() > 0.7) {
            board.queue.add(ProductionBoard.UnitItem(FTTConfig.SUPPLY))
            NodeStatus.SUCCEEDED
        } else
            NodeStatus.FAILED
    }
}

class DetectorProduction : Node<ProductionBoard> {
    override fun tick(board: ProductionBoard): NodeStatus {
        return if (StrategyUF.needMobileDetection() > 0.7 && board.queue.none { it is ProductionBoard.UnitItem && it.type.isDetector }) {
            val toBuild = PlayerUnit.getMissingUnits(UnitQuery.myUnits, UnitType.Terran_Comsat_Station.allRequiredUnits())
                    .firstOrNull { ut ->
                        FTTBot.self.canMake(ut)
                                && board.orderedConstructions.none { it.type == ut }
                                && board.queue.none { it is ProductionBoard.UnitItem && it.type == ut }
                    } ?: return NodeStatus.FAILED
            board.queue.offer(ProductionBoard.UnitItem(toBuild))
            NodeStatus.SUCCEEDED
        } else
            NodeStatus.FAILED
    }
}

class ProduceAttacker : Node<ProductionBoard> {
    override fun tick(board: ProductionBoard): NodeStatus {
        if (!board.queue.isEmpty()) return NodeStatus.FAILED
        val added = UnitQuery.myUnits.filter { it is TrainingFacility && !it.isTraining && it.trainingQueueSize == 0 }.map { (it as TrainingFacility).trains() }
                .flatten()
                .filter { it.canAttack() && !it.isWorker && it.canMove() && FTTBot.self.canMake(it) }
                .groupBy { it }
                .map { (ut, l) -> ut to l.size }
                .shuffled()
                .sumBy { (type, count) ->
                    val todo = count - board.queue.count { (it as? ProductionBoard.UnitItem)?.type == type }
                    repeat(todo) { board.queue.add(ProductionBoard.UnitItem(type)) }
                    todo
                }
        if (added > 0) return NodeStatus.SUCCEEDED
        return NodeStatus.FAILED
    }
}

class BuildNextItemFromProductionQueue : Node<ProductionBoard> {
    private val LOG = Logger.getLogger(this::class.java.simpleName)

    override fun tick(board: ProductionBoard): NodeStatus {
        val queue = board.queue

        orderedConstructions.removeIf { it.building?.isCompleted ?: false || it.started && FTTConfig.MY_RACE != Race.Terran }
        val abortedConstructions = orderedConstructions.minus(UnitQuery.myWorkers.map { it.board.goal }.filterIsInstance(Construction::class.java))
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
            with(board) {
                reservedGas += toBuild.type.gasPrice()
                reservedMinerals += toBuild.type.mineralPrice()
            }
            queue.pop()
        }

        return if (queue.isEmpty()) NodeStatus.FAILED else NodeStatus.RUNNING
    }

    private fun continueConstruction(construction: Construction) {
        if (!construction.started) {
            construction.position = ConstructionPosition.findPositionFor(construction.type) ?: return
        }
        val worker = findWorker(construction.position.toPosition()) ?: return
        worker.board.goal = construction
    }

    private fun construct(toBuild: ProductionBoard.UnitItem): Boolean {
        val position = ConstructionPosition.findPositionFor(toBuild.type) ?: return false
        val worker = toBuild.favoriteProducer as? Worker<*> ?: findWorker(position.toPosition()) ?: return false
        val construction = Construction(toBuild.type, position)
        worker.board.goal = construction
        orderedConstructions.offer(construction)
        return true
    }
}