package org.styx.tasks

import bwapi.Color
import bwapi.Unit
import org.styx.*
import org.styx.Context.command
import org.styx.Context.find
import org.styx.Context.game
import org.styx.Context.reserve
import org.styx.Context.visualization

class GatherMinerals(private val worker: Unit) : Task() {
    override val priority: Int = 1

    override fun performExecute(): FlowStatus {
        if (!reserve.units.contains(worker)) return FlowStatus.FAILED
        reserve.reserveUnit(worker)
        if (worker.isCarryingMinerals) {
            mineralAssignedTo -= worker
            return FlowStatus.RUNNING
        }
        val target = mineralAssignedTo.compute(worker) { worker, mineral ->
            mineral
                    ?: (find.minerals - mineralAssignedTo.values).minBy { it.getDistance(worker) }
        } ?: return FlowStatus.FAILED
        if (visualization.drawMineralLocks) {
            game.drawBoxMap(target.topLeft, target.bottomRight, Color.Blue)
        }
        if (!worker.isGatheringMinerals || worker.target != target) {
            command.gather(worker, target)
        }
        return FlowStatus.RUNNING
    }

    companion object {
        val mineralAssignedTo = mutableMapOf<Unit, Unit>()
    }
}

class Gathering : TaskProvider {
    override fun invoke(): List<Task> =
            find.myWorkers.map(::GatherMinerals)
}