package org.fttbot.task

import org.fttbot.Board.resources
import org.fttbot.info.UnitQuery
import org.fttbot.info.closestTo
import org.openbw.bwapi4j.unit.Worker

class GatherMinerals : Task() {
    override val utility: Double
        get() = 0.1

    override fun toString(): String = "Gather minerals"

    override fun processInternal() : TaskStatus {
        val workers = resources.units.filterIsInstance<Worker>()
        workers.forEach { worker ->
            resources.reserveUnit(worker)
            if (!worker.isGatheringMinerals) {
                val mineral = UnitQuery.minerals.closestTo(worker) ?: return TaskStatus.FAILED
                worker.gather(mineral)
            }
        }
        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {
        override fun invoke(): List<Task> = listOf(GatherMinerals())
    }
}