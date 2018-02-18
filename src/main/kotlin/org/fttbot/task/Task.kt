package org.fttbot.task

import com.badlogic.gdx.utils.Array
import org.fttbot.FTTBot
import org.fttbot.info.Cluster
import org.fttbot.info.ClusterUnitInfo
import org.fttbot.info.UnitQuery
import org.fttbot.info.board
import org.openbw.bwapi4j.unit.ComsatStation
import org.openbw.bwapi4j.unit.PlayerUnit

interface Task {
    val utility: Double
    fun run(available: Resources): TaskResult

    companion object {
        var repairTaskScale = 1.0
        val lastTaskOrder = ArrayList<Task>()
        private val tasks = mutableSetOf(GatherResources, ResetGoal, DetectorTask, ResumeConstructionsTask,
                ProductionQueueTask, HandleSupplyBlock, SupplyBuilderTask, WorkerProduction,
                TrainUnits, BuildAddons,
//                DetectorProduction,
                DoUpgrades, ProduceAttacker, BoSearch)

        fun step() {
            RepairTask.provideTasksTo(tasks)
            WorkerDefence.provideTasksTo(tasks)
            ScoutingTask.provideTasksTo(tasks)
            RepairerCompanyTask.provideTasksTo(tasks)
            val tasksToProcess = Array<Task>(false, 20)
            tasks.forEach(tasksToProcess::add)
            lastTaskOrder.clear()

            val availableUnits = UnitQuery.myUnits.filter { it.isCompleted }.toMutableList()
            var minerals = FTTBot.self.minerals()
            var gas = FTTBot.self.gas()
            var supply = FTTBot.self.supplyTotal() - FTTBot.self.supplyUsed()

            while (tasksToProcess.size > 0) {
                val taskIndex = tasksToProcess.selectRankedIndex({ a, b -> b.utility.compareTo(a.utility) }, 1)
                val task = tasksToProcess.removeIndex(taskIndex)
                lastTaskOrder.add(task)
                val taskResult = task.run(Resources(availableUnits, minerals, gas, supply))
                val used = taskResult.used
                val usedUnits = used.units
                minerals -= used.minerals
                gas -= used.gas
                supply -= used.supply
                if (!availableUnits.containsAll(usedUnits)) throw IllegalStateException("Some units ($usedUnits)that $task wants to use are not available!")
                availableUnits.removeAll(usedUnits)
                if (taskResult.status != TaskStatus.RUNNING) {
                    tasks.remove(task)
                }
            }
        }
    }
}

class TaskResult(val used: Resources = Resources(), val status: TaskStatus = TaskStatus.RUNNING) {
    companion object {
        val RUNNING = TaskResult()
    }
}

class Resources(val units: Collection<PlayerUnit> = emptyList(), val minerals: Int = 0, val gas: Int = 0, val supply: Int = 0) {
    operator fun plus(resources: Resources) = Resources(units + resources.units,
            minerals + resources.minerals,
            gas + resources.gas,
            supply + resources.supply)
}

enum class TaskStatus { SUCCESSFUL, FAILED, RUNNING }


object ResetGoal : Task {
    override val utility: Double = 0.0

    override fun run(available: Resources): TaskResult {
        available.units.forEach { if (it.userData != null) it.board.goal = null }
        return TaskResult(Resources(), TaskStatus.RUNNING)
    }
}

object DetectorTask : Task {
    override val utility: Double
        get() = 0.5

    override fun run(available: Resources): TaskResult {
        val clusterNeedingDetection = Cluster.mobileCombatUnits.firstOrNull { ClusterUnitInfo.getInfo(it).needDetection > 0.55 }
                ?: return TaskResult()
        val comsat = available.units.firstOrNull { it is ComsatStation } as? ComsatStation ?: return TaskResult()
        comsat.scannerSweep(ClusterUnitInfo.getInfo(clusterNeedingDetection).forceCenter)
        return TaskResult(Resources(listOf(comsat)))
    }

}