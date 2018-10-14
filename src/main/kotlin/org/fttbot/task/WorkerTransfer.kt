package org.fttbot.task

import org.fttbot.ResourcesBoard
import org.fttbot.info.MyInfo
import org.fttbot.info.RadiusCache
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.unit.ResourceDepot
import org.openbw.bwapi4j.unit.Worker

class BaseWorkers(val base: ResourceDepot, val workers: List<Worker>, val minerals: Int, val gas: Int)

class WorkerTransfer : Task() {
    override val utility: Double
        get() = 0.15

    private val workersInTransfer = mutableMapOf<Worker, ResourceDepot>()
    private val transferTasks = MParallelTask(ManagedTaskProvider({ workersInTransfer.entries.toList() }) {
        SafeMove(it.key, it.value.position).neverFail()
    })

    override fun processInternal(): TaskStatus {
        workersInTransfer.keys.retainAll(ResourcesBoard.units)
        workersInTransfer.entries.removeIf {
            it.key.position.getDistance(it.value.position) < 200
        }
        val potentialWorkers = RadiusCache((UnitQuery.myWorkers - workersInTransfer.keys) intersect ResourcesBoard.units.filterIsInstance<Worker>())

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
        private val workerTransfer: Task = WorkerTransfer().nvr()

        override fun invoke(): List<Task> = listOf(workerTransfer)

    }
}

