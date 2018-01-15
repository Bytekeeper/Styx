package org.fttbot.task

import com.badlogic.gdx.utils.Array
import org.fttbot.behavior.*
import org.fttbot.decision.Utilities
import org.fttbot.info.UnitQuery
import org.fttbot.info.board
import org.fttbot.info.isMyUnit
import org.fttbot.search.EPS
import org.omg.PortableInterceptor.SUCCESSFUL
import org.openbw.bwapi4j.unit.*

interface Task {
    companion object {
        var repairTaskScale = 1.0
        private val tasks = HashSet<Task>()

        fun step() {
            val availableUnits = UnitQuery.myUnits.filter { it.isCompleted }.toMutableList()
            RepairTask.provideTasksTo(tasks)
            WorkerDefence.provideTasksTo(tasks)
            ScoutingTask.provideTasksTo(tasks)
            RepairerCompanyTask.provideTasksTo(tasks)
            if (tasks.none { it is GatherResources }) {
                tasks.add(GatherResources)
            }
            if (tasks.none { it is ResetGoal }) {
                tasks.add(ResetGoal)
            }
            val tasksToProcess = Array<Task>(false, 20)
            tasks.forEach(tasksToProcess::add)
            while (tasksToProcess.size > 0) {
                val taskIndex = tasksToProcess.selectRankedIndex({a,b -> b.utility.compareTo(a.utility)}, 1)
                val task = tasksToProcess.removeIndex(taskIndex)
                val taskResult = task.run(availableUnits)
                val usedUnits = taskResult.usedUnits
                if (!availableUnits.containsAll(usedUnits)) throw IllegalStateException("Some units that $task wants to use are not available!")
                availableUnits.removeAll(usedUnits)
                if (taskResult.status != TaskStatus.RUNNING) {
                    tasks.remove(task)
                }
            }
        }
    }

    val utility: Double
    fun run(units: List<PlayerUnit>): TaskResult
}

class TaskResult(val usedUnits: Collection<PlayerUnit>, val status: TaskStatus = TaskStatus.RUNNING)

enum class TaskStatus { SUCCESSFUL, FAILED, RUNNING }

class WorkerDefence(val base: PlayerUnit) : Task {
    override val utility: Double
        get() = Utilities.danger(base.board)

    override fun run(units: List<PlayerUnit>): TaskResult {
        val potentialWorkerDefenders = units.filter { it is Worker<*> && it.getDistance(base) < 300 }
        val numWorkerDefenders = (potentialWorkerDefenders.size * utility).toInt()
        val alreadyDefending = potentialWorkerDefenders.filter { it.board.goal is Defending }
        if (alreadyDefending.size > numWorkerDefenders) {
            // TODO Choose "best" not "any"
            return TaskResult(potentialWorkerDefenders.subList(0, numWorkerDefenders), TaskStatus.SUCCESSFUL)
        }
        val newDefenders = potentialWorkerDefenders.filter { it.board.goal !is Defending }.take(numWorkerDefenders - alreadyDefending.size)
        newDefenders.forEach { it.board.goal = Defending() }
        return TaskResult(newDefenders, TaskStatus.SUCCESSFUL)
    }

    companion object {
        fun provideTasksTo(tasks: MutableSet<Task>) {
            val enemies = UnitQuery.enemyUnits
            UnitQuery.myBases.mapNotNull { base ->
                val relevantEnemies = base.getUnitsInRadius(300, enemies)
                if (relevantEnemies.isEmpty()) return@mapNotNull null
                tasks.add(WorkerDefence(base))
            }
        }
    }
}

const val RESOURCE_RANGE = 300

object GatherResources : Task {
    override val utility: Double = EPS

    override fun run(units: List<PlayerUnit>): TaskResult {
        val myBases = UnitQuery.ownedUnits.filter { it.isMyUnit && it is Base }

        val workerUnits = myBases.flatMap { base ->
            val relevantUnits = UnitQuery.unitsInRadius(base.position, RESOURCE_RANGE)
            val refineries = relevantUnits.filter { it is GasMiningFacility && it.isMyUnit && it.isCompleted }.map { it as GasMiningFacility }
            val remainingWorkers = units.filter { it.getDistance(base) < RESOURCE_RANGE && it is Worker<*> && it.isMyUnit && it.isCompleted }.map { it as Worker<*> }
            val workersToAssign = remainingWorkers.toMutableList()
            val minerals = relevantUnits.filter { it is MineralPatch }.map { it as MineralPatch }.toMutableList()

            val gasMissing = refineries.size * 3 - workersToAssign.count { it.isGatheringGas }
            if (gasMissing > 0) {
                val refinery = refineries.first()
                repeat(gasMissing) {
                    val worker = workersToAssign.firstOrNull { it.isIdle || it.isGatheringMinerals && !it.isCarryingMinerals }
                            ?: workersToAssign.firstOrNull { it.isGatheringMinerals } ?: return@repeat
                    worker.board.goal = Gathering(refinery)
                    workersToAssign.remove(worker)
                }
            } else if (gasMissing < 0) {
                repeat(-gasMissing) {
                    val worker = workersToAssign.firstOrNull { it.isGatheringGas && !it.isCarryingGas }
                            ?: workersToAssign.firstOrNull { it.isGatheringGas } ?: return@repeat
                    val targetMineral = (minerals.filter { !it.isBeingGathered }.minBy { it.getDistance(worker) }
                            ?: minerals.minBy { it.getDistance(worker) })
                    if (targetMineral != null) {
                        worker.board.goal = Gathering(targetMineral)
                        minerals.remove(targetMineral)
                        workersToAssign.remove(worker)
                    }
                }
            }
            workersToAssign.filter { it.board.goal !is Construction && it.board.goal !is Gathering }
                    .forEach { worker ->
                        val targetMineral = (minerals.filter { !it.isBeingGathered }.minBy { it.getDistance(worker) }
                                ?: minerals.minBy { it.getDistance(worker) })
                        if (targetMineral != null) {
                            worker.board.goal = Gathering(targetMineral)
                            minerals.remove(targetMineral)
                        }
                    }
            return@flatMap remainingWorkers
        }
        return TaskResult(workerUnits)
    }

}

object ResetGoal : Task {
    override val utility: Double = 0.0

    override fun run(units: List<PlayerUnit>): TaskResult {
        units.forEach { if (it.userData != null) it.board.goal = null }
        return TaskResult(emptyList(), TaskStatus.RUNNING)
    }

}