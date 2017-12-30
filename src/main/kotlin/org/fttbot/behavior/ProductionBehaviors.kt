package org.fttbot.behavior

import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.ConstructionPosition
import org.fttbot.FTTBot
import org.fttbot.FTTConfig
import org.fttbot.behavior.ProductionBoard.orderedConstructions
import org.fttbot.board
import org.fttbot.layer.*
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
        if (!canAfford(item.type)) return Status.FAILED
        if (!unit.canTrain(item.type)) return Status.FAILED
        if (!unit.train(item.type) && (unit !is ExtendibleByAddon || !unit.build(item.type))) {
            LOG.severe("${unit} couldn't start training/building ${item.type}")
            return Status.FAILED
        }
        LOG.info("${unit} started training ${item.type}")
        ProductionBoard.queue.pop()
        return Status.RUNNING
    }

    private fun canAfford(type: UnitType) =
            ProductionBoard.reservedGas + type.gasPrice() <= FTTBot.self.gas()
                    && ProductionBoard.reservedMinerals + type.mineralPrice() <= FTTBot.self.minerals()
}

class WorkerProduction : LeafTask<ProductionBoard>() {
    override fun execute(): Status {
        UnitQuery.myBases.forEach { base ->
            val units = UnitQuery.unitsInRadius(base.position, 300)
            val workerDelta = units.map { if (it is MineralPatch) 2 else if (it is GasMiningFacility) 3 else if (it is MobileUnit && it.isWorker && it.isMyUnit) -1 else 0 }.sum()
            if (workerDelta > 0 && !board().queue.any { it.type.isWorker && it.favoriteBuilder == base }) {
                board().queue.add(ProductionBoard.Item(FTTConfig.WORKER, base))
                return Status.SUCCEEDED
            }
        }
        return Status.FAILED
    }

    override fun copyTo(task: Task<ProductionBoard>): Task<ProductionBoard> = task
}

class SupplyProduction : LeafTask<ProductionBoard>() {
    override fun execute(): Status {
        val self = FTTBot.self
        if (self.supplyTotal() >= 400) return Status.FAILED
        val supplyDelta = self.supplyTotal() - self.supplyUsed() -
                UnitQuery.myUnits.filter { it is TrainingFacility }.count() * 2
        if (supplyDelta < 0 &&
                orderedConstructions.sumBy { it.type.supplyProvided() } + board().queue.sumBy{ it.type.supplyProvided() } < -supplyDelta) {
            board().queue.add(ProductionBoard.Item(FTTConfig.SUPPLY))
            return Status.SUCCEEDED
        }
        return Status.FAILED
    }

    override fun copyTo(task: Task<ProductionBoard>): Task<ProductionBoard> = task
}

class ProduceAttacker : LeafTask<ProductionBoard>() {
    override fun execute(): Status {
        if (!board().queue.isEmpty()) return Status.FAILED
        val added = UnitQuery.myUnits.filter { it is TrainingFacility && !it.isTraining && it.trainingQueueSize == 0 }.map { (it as TrainingFacility).trains() }
                .flatten()
                .filter { it.canAttack() && !it.isWorker && it.canMove() && FTTBot.self.canMake(it)}
                .groupBy { it }
                .map { (ut, l) -> ut to l.size }
                .shuffled()
                .sumBy { (type, count) ->
                    val todo = count - board().queue.count { it.type == type }
                    repeat(todo) { board().queue.add(ProductionBoard.Item(type))}
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

        while (!queue.isEmpty() && canAfford(queue.peek().type)) {
            val peekedItem = queue.peek()
            if (!FTTBot.self.canMake(peekedItem.type)) {
                if (peekedItem.type.supplyRequired() > 0 && orderedConstructions.any { it.type.supplyProvided() > 0 }) {
                    LOG.finest("Can't build ${peekedItem}.type} yet, but supply is being constructed")
                    break
                } else if (orderedConstructions.any { it.type == peekedItem.type.whatBuilds().first }) {
                    LOG.finest("Can't build ${peekedItem.type} yet, but dependency is being built")
                    break
                } else {
                    val item = queue.pop()
                    LOG.severe("Skipping over build item ${item.type}, can't build it")
                }
                continue
            }
            val toBuild = queue.peek()

            val builderType = toBuild.type.whatBuilds().first
            if (builderType.isWorker) {
                if (!construct(toBuild)) break
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

    private fun construct(toBuild: ProductionBoard.Item): Boolean {
        val position = ConstructionPosition.findPositionFor(toBuild.type) ?: return false
        val worker = toBuild.favoriteBuilder as? Worker<*> ?: findWorker(position.toPosition()) ?: return false
        worker.board.construction = Construction(toBuild.type, position)
        orderedConstructions.offer(worker.board.construction)
        return true
    }

    private fun canAfford(type: UnitType) =
            `object`.reservedGas + type.gasPrice() <= FTTBot.self.gas() && `object`.reservedMinerals + type.mineralPrice() <= FTTBot.self.minerals()


    override fun copyTo(task: Task<ProductionBoard>?): Task<ProductionBoard> = BuildNextItemFromProductionQueue()
}