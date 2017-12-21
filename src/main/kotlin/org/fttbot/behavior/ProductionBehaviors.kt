package org.fttbot.behavior

import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.ConstructionPosition
import org.fttbot.FTTBot
import org.fttbot.board
import org.fttbot.layer.FUnit
import org.fttbot.layer.FUnitType

class Train : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        if (unit.isTraining) return Status.RUNNING
        if (ProductionBoard.queue.isEmpty()) return Status.SUCCEEDED
        val item = ProductionBoard.queue.peek()
        if (!unit.canMake(item.type) || !canAfford(item.type)) return Status.FAILED
        if (!unit.train(item.type)) return Status.FAILED
        ProductionBoard.queue.pop()
        return Status.RUNNING
    }

    override fun cpy(): Task<BBUnit> = Train()

    private fun canAfford(type: FUnitType) =
            ProductionBoard.reservedGas + type.gasPrice <= FTTBot.self.gas()
                    && ProductionBoard.reservedMinerals + type.mineralPrice <= FTTBot.self.minerals()
}

class BuildNextItemFromProductionQueue : LeafTask<ProductionBoard>() {
    override fun execute(): Status {
        with(`object`) {
            if (queueNeedsRebuild) {
                reservedMinerals = 0;
                reservedGas = 0;
                FUnit.myUnits().filter { it.board.construction?.started == false }
                        .forEach {
                            val construction = it.board.construction!!
                            reservedMinerals += construction.type.mineralPrice
                            reservedGas += construction.type.gasPrice
                        }
            }
        }
        val queue = `object`.queue

        while (!queue.isEmpty() && canAfford(queue.peek().type) && FTTBot.game.canMake(queue.peek().type.type)) {
            val toBuild = queue.peek()

            val builderType = toBuild.type.whatBuilds.first
            if (builderType.type.isWorker) {
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

    private fun construct(toBuild: ProductionBoard.Item): Boolean {
        val position = ConstructionPosition.findPositionFor(toBuild.type) ?: return false
        val worker = findWorker(position.toPosition()) ?: return false
        worker.board.construction = Construction(toBuild.type, position)
        return true
    }

    private fun canAfford(type: FUnitType) =
            `object`.reservedGas + type.gasPrice <= FTTBot.self.gas() && `object`.reservedMinerals + type.mineralPrice <= FTTBot.self.minerals()


    override fun copyTo(task: Task<ProductionBoard>?): Task<ProductionBoard> = BuildNextItemFromProductionQueue()
}