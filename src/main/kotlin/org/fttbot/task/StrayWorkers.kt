package org.fttbot.task

import org.fttbot.ResourcesBoard
import org.fttbot.info.UnitQuery
import org.fttbot.info.closestTo
import org.openbw.bwapi4j.unit.ResourceDepot
import org.openbw.bwapi4j.unit.Worker

class StrayWorkers : Task() {
    override val utility: Double = 0.12

    private val strayWorkers = mutableMapOf<Worker, ResourceDepot>()
    private val returnTasks = MParallelTask(ManagedTaskProvider({ strayWorkers.entries.toList() }) {
        Move(it.key, it.value.position).neverFail()
    })

    override fun processInternal(): TaskStatus {
        strayWorkers.keys.retainAll(ResourcesBoard.units)
        strayWorkers.entries.removeIf { it.key.position.getDistance(it.value.position) < 200 || !it.key.exists()}
        strayWorkers += (ResourcesBoard.completedUnits - strayWorkers.keys)
                .asSequence()
                .filterIsInstance<Worker>()
                .filter { UnitQuery.my<ResourceDepot>().inRadius(it, 300).none() }
                .mapNotNull {
                    it to (UnitQuery.my<ResourceDepot>().closestTo(it) ?: return@mapNotNull null)
                }
                .toList()

        strayWorkers.keys.forEach { ResourcesBoard.reserveUnit(it) }

        return returnTasks.process()
    }

    companion object : TaskProvider {
        private val strayWorkers: Task = StrayWorkers().nvr()

        override fun invoke(): List<Task> = listOf(strayWorkers)

    }
}