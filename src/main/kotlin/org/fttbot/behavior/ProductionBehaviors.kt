package org.fttbot.behavior

import bwapi.UnitCommand.train
import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.FTTBot
import org.fttbot.layer.FUnit
import org.fttbot.layer.FUnitType

class Train : LeafTask<BBUnit>() {
    init {
        guard = Guard()
    }

    override fun execute(): Status {
        val order = `object`.order as Training
        val unit = `object`.unit
        if (FTTBot.game.canMake(order.type.type)
                && !unit.isTraining && unit.train(order.type)) {
            `object`.order = Order.NONE
            return Status.SUCCEEDED
        }
        return Status.RUNNING
    }

    override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Train()

    class Guard : LeafTask<BBUnit>() {
        override fun execute(): Status = if (`object`.order is Training) Status.SUCCEEDED else Status.FAILED

        override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Guard()

    }
}

class BuildNextItemFromProductionQueue : LeafTask<Production>() {
    override fun execute(): Status {
        with(`object`) {
            if (queueNeedsRebuild) {
                reservedMinerals = 0;
                reservedGas = 0;
                FUnit.myUnits().filter { (BBUnit.of(it).order as? Construction)?.started == false }
                        .forEach {
                            val construction = BBUnit.of(it).order as Construction
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
            } else if (!train(builderType, toBuild)) break
            with(`object`) {
                reservedGas += toBuild.type.gasPrice
                reservedMinerals += toBuild.type.mineralPrice
            }
            queue.pop()
        }

        return if (queue.isEmpty()) Status.SUCCEEDED else Status.RUNNING
    }

    private fun construct(toBuild: Production.Item): Boolean {
        val worker = FUnit.myWorkers().firstOrNull { BBUnit.of(it).order is Gathering }
                ?: FUnit.myWorkers().firstOrNull { !it.isCarryingMinerals && !it.isCarryingGas && !it.isConstructing}
                ?: FUnit.myWorkers().firstOrNull { !it.isConstructing}
                ?: return false
        BBUnit.of(worker).order = Construction(toBuild.type)
        return true
    }

    private fun train(builderType: FUnitType, toBuild: Production.Item): Boolean {
        val builder = FUnit.myUnits().filter { it.type == builderType && BBUnit.of(it).order == Order.NONE && !it.isTraining }
                .firstOrNull()
                ?: return false
        BBUnit.of(builder).order = Training(toBuild.type)
        return true
    }

    private fun canAfford(type: FUnitType) =
            `object`.reservedGas + type.gasPrice <= FTTBot.self.gas() && `object`.reservedMinerals + type.mineralPrice <= FTTBot.self.minerals()


    override fun copyTo(task: Task<Production>?): Task<Production> = BuildNextItemFromProductionQueue()
}