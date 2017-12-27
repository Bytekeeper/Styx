package org.fttbot.behavior

import bwapi.Race
import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.ConstructionPosition
import org.fttbot.FTTBot
import org.fttbot.FTTConfig
import org.fttbot.board
import org.fttbot.import.FUnitType
import org.fttbot.layer.FUnit
import java.util.*

class Train : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        if (unit.isTraining) return Status.RUNNING
        if (ProductionBoard.queue.isEmpty()) return Status.SUCCEEDED
        val item = ProductionBoard.queue.peek()
        if (!unit.canMake(item.type) || !canAfford(item.type)) return Status.FAILED
        if (!unit.train(item.type)) return Status.FAILED
        LOG.info("${unit} started training ${item.type}")
        ProductionBoard.queue.pop()
        return Status.RUNNING
    }

    private fun canAfford(type: FUnitType) =
            ProductionBoard.reservedGas + type.gasPrice <= FTTBot.self.gas()
                    && ProductionBoard.reservedMinerals + type.mineralPrice <= FTTBot.self.minerals()
}

class BuildNextItemFromProductionQueue : LeafTask<ProductionBoard>() {
    val orderedConstructions = ArrayDeque<Construction>()

    override fun execute(): Status {
        with(board()) {
            if (queueNeedsRebuild) {
                reservedMinerals = 0
                reservedGas = 0
                orderedConstructions.filter { !it.started }
                        .forEach {
                            reservedMinerals += it.type.mineralPrice
                            reservedGas += it.type.gasPrice
                        }
            }
        }
        val queue = `object`.queue

        orderedConstructions.removeIf { it.building?.isCompleted ?: false || it.started && FTTConfig.MY_RACE != Race.Terran}
        val abortedConstructions = orderedConstructions.minus(FUnit.myWorkers().filter { it.board.construction != null }.map { it.board.construction!! })
        abortedConstructions.forEach { continueConstruction(it) }

        while (!queue.isEmpty() && canAfford(queue.peek().type) && FTTBot.game.canMake(queue.peek().type.source)) {
            val toBuild = queue.peek()

            val builderType = toBuild.type.whatBuilds.first
            if (builderType.isWorker) {
                if (!construct(toBuild)) break
            } else break
            with(`object`) {
                reservedGas += toBuild.type.gasPrice
                reservedMinerals += toBuild.type.mineralPrice
            }
            queue.pop()
        }

        return if (queue.isEmpty()) Status.SUCCEEDED else Status.RUNNING
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
        val worker = findWorker(position.toPosition()) ?: return false
        worker.board.construction = Construction(toBuild.type, position)
        orderedConstructions.offer(worker.board.construction)
        return true
    }

    private fun canAfford(type: FUnitType) =
            `object`.reservedGas + type.gasPrice <= FTTBot.self.gas() && `object`.reservedMinerals + type.mineralPrice <= FTTBot.self.minerals()


    override fun copyTo(task: Task<ProductionBoard>?): Task<ProductionBoard> = BuildNextItemFromProductionQueue()
}