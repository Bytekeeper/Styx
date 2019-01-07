package org.styx.tasks

import bwapi.UnitType
import org.styx.Context.command
import org.styx.Context.reserve
import org.styx.FlowStatus
import org.styx.Task
import org.styx.UnitLock

class TrainTask(override val priority: Int, private val type: UnitType) : Task() {
    private val trainerLock = UnitLock { it.type == type.whatBuilds().first && it.trainingQueue.isEmpty() }

    override fun reset() {
        super.reset()
        trainerLock.reset()
    }

    override fun performExecute(): FlowStatus {
        if (trainerLock.current?.trainingQueue?.contains(type) == true) {
            return FlowStatus.DONE
        }
        if (reserve.acquireFor(type)) {
            val trainer = trainerLock.lockOr { reserve.units.firstOrNull { it.type == type.whatBuilds().first && it.trainingQueue.isEmpty() } }
                    ?: return FlowStatus.RUNNING
            command.train(trainer, type)
        }
        return FlowStatus.RUNNING
    }

}
