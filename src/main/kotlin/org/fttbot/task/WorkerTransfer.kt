package org.fttbot.task

import org.fttbot.ProductionBoard
import org.fttbot.ResourcesBoard
import org.fttbot.info.MyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.info.closestTo
import org.fttbot.info.inRadius
import org.openbw.bwapi4j.unit.ResourceDepot
import org.openbw.bwapi4j.unit.Worker

class BaseWorkers(val base: ResourceDepot, val workers: List<Worker>, val minerals: Int, val gas: Int)

class WorkerTransfer : Task() {
    override val utility: Double
        get() = 0.15

    private val workersInTransfer = mutableMapOf<Worker, ResourceDepot>()
    private val transferTasks = MParallelTask(ManagedTaskProvider({ workersInTransfer.entries.toList() }) {
        Move(it.key, it.value.position).neverFail()
    })

    override fun processInternal(): TaskStatus {
        workersInTransfer.keys.retainAll(ResourcesBoard.units)
        workersInTransfer.entries.removeIf {
            it.key.position.getDistance(it.value.position) < 200
        }
        val potentialWorkers = (UnitQuery.myWorkers - workersInTransfer.keys) intersect ResourcesBoard.units.filterIsInstance<Worker>()

        val baseInfos = MyInfo.myBases
                .filter { it.isCompleted }
                .map {
                    val workers = potentialWorkers.inRadius(it, 300) - workersInTransfer.keys
                    BaseWorkers(it, workers, UnitQuery.minerals.inRadius(it, 300).count(), UnitQuery.geysers.inRadius(it, 300).count())
                }.sortedByDescending { it.workers.size / (it.minerals + 2.0 * it.gas) }

        val last = baseInfos.lastOrNull() ?: return TaskStatus.RUNNING
        val lastScore = last.workers.size / (last.minerals + 2.0 * last.gas)
        if (lastScore > 0.9) return TaskStatus.RUNNING

        baseInfos.filter { it.workers.size / (it.minerals + 2.0 * it.gas) > 1.6 }
                .forEach {
                    val workersToMove = (it.workers.size - (it.minerals + 2.0 * it.gas) * 1.2).toInt()
                    it.workers
                            .sortedBy {
                                (if (it.isGatheringMinerals) 2 else 0) +
                                        (if (it.isGatheringGas) 1 else 0)
                            }
                            .takeLast(workersToMove)
                            .forEach {
                                workersInTransfer.computeIfAbsent(it) { _ ->
                                    last.base
                                }
                            }

                }

        workersInTransfer.keys.forEach { ResourcesBoard.reserveUnit(it) }

        return transferTasks.process()
    }

    companion object : TaskProvider {
        private val workerTransfer: Task = WorkerTransfer().neverFail().repeat()

        override fun invoke(): List<Task> = listOf(workerTransfer)

    }
}

class StrayWorkers : Task() {
    override val utility: Double = 0.12

    private val strayWorkers = mutableMapOf<Worker, ResourceDepot>()
    private val returnTasks = MParallelTask(ManagedTaskProvider({ strayWorkers.entries.toList() }) {
        Move(it.key, it.value.position).neverFail()
    })

    override fun processInternal(): TaskStatus {
        strayWorkers.keys.retainAll(ResourcesBoard.units)
        strayWorkers.entries.removeIf { it.key.position.getDistance(it.value.position) < 200 }
        strayWorkers += (ResourcesBoard.units - strayWorkers.keys)
                .filterIsInstance<Worker>()
                .filter { UnitQuery.myBases.inRadius(it, 300).none() }
                .mapNotNull {
                    it to (UnitQuery.myBases.closestTo(it) ?: return@mapNotNull null)
                }

        strayWorkers.keys.forEach { ResourcesBoard.reserveUnit(it) }

        return returnTasks.process()
    }

    companion object : TaskProvider {
        private val strayWorkers: Task = StrayWorkers().neverFail().repeat()

        override fun invoke(): List<Task> = listOf(strayWorkers)

    }
}