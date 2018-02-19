package org.fttbot.task

import com.badlogic.gdx.math.MathUtils.clamp
import org.fttbot.FTTBot
import org.fttbot.behavior.BeRepairedBy
import org.fttbot.behavior.IdleAt
import org.fttbot.behavior.Repairing
import org.fttbot.div
import org.fttbot.info.Cluster
import org.fttbot.info.UnitQuery
import org.fttbot.info.board
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.Color
import org.openbw.bwapi4j.unit.Mechanical
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.SCV
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

const val MAX_REPAIR_GROUPS = 3

class RepairerCompanyTask(var area: Position) : Task {
    private val currentRepairers = HashSet<SCV>()
    private val relevantUnits
        get() = UnitQuery.myUnits.count {
            it.getDistance(area) < 300 && it !is SCV && it is Mechanical && it is MobileUnit
        }
    override val utility: Double
        get() = clamp(relevantUnits / 100.0, 0.0, 1.0)

    override fun run(available: Resources): TaskResult {
        val units = available.units
        val numRepairers = max(0, sqrt(relevantUnits.toDouble()).toInt() / 2)
        currentRepairers.retainAll(units)
        while (currentRepairers.size > numRepairers + 1) {
            currentRepairers.remove(currentRepairers.first())
        }
        val additionalRepairers = (units.filterIsInstance(SCV::class.java).sortedBy { it.getDistance(area) } - currentRepairers)
        val workersToAdd = min(numRepairers - currentRepairers.size, additionalRepairers.size)
        if (workersToAdd > 0) {
            currentRepairers.addAll(additionalRepairers.subList(0, workersToAdd))
        }
        if (currentRepairers.isEmpty()) return TaskResult(Resources(currentRepairers), TaskStatus.FAILED)
        currentRepairers.forEach {
            it.board.goal = IdleAt(area)
        }
        return TaskResult(Resources(currentRepairers), TaskStatus.RUNNING)
    }

    companion object {
        fun provideTasksTo(tasks: MutableSet<Task>) {
            val existing = tasks.filterIsInstance(RepairerCompanyTask::class.java).toMutableList()
            val clusters = Cluster.mobileCombatUnits.filter { it.units.any { it is Mechanical } }
                    .sortedBy { it.units.size }
                    .takeLast(MAX_REPAIR_GROUPS)
                    .toMutableList()
            if (clusters.size < existing.size) {
                val toRemove = existing.subList(clusters.size, existing.size)
                tasks.removeAll(toRemove)
                toRemove.clear()
            }
            existing.forEach { task ->
                val cluster = clusters.minBy { task.area.getDistance(it.position) } ?: throw IllegalStateException("Not enough clusters!")
                task.area = cluster.position
                clusters.remove(cluster)
            }
            clusters.forEach {
                tasks.add(RepairerCompanyTask(it.position))
            }
        }
    }
}

class RepairTask(val target: PlayerUnit) : Task {
    override val utility: Double get() = (1 - target.hitPoints * target.hitPoints / target.maxHitPoints().toDouble() / target.maxHitPoints()) * 1.0 * Task.repairTaskScale

    override fun run(available: Resources): TaskResult {
        val units = available.units
        if (FTTBot.self.minerals() < 10) return TaskResult(status = TaskStatus.FAILED)
        if (target.hitPoints >= target.maxHitPoints()) return TaskResult(status =  TaskStatus.SUCCESSFUL)
        if (!units.contains(target)) {
            return TaskResult(status = TaskStatus.FAILED)
        }
        val repairer =
                if (target is MobileUnit && target.board.goal is BeRepairedBy && units.contains((target.board.goal as BeRepairedBy).worker))
                    (target.board.goal as BeRepairedBy).worker
                else {
                    val existingRepairer = if (target !is MobileUnit) {
                        units.filterIsInstance(SCV::class.java).filter { (it.board.goal as? Repairing)?.target == target && units.contains(it) }.firstOrNull()
                    } else null
                    existingRepairer ?: units.filterIsInstance(SCV::class.java).filter { it != target && units.contains(it) }.minBy { it.getDistance(target) }
                }
        if (repairer == null || repairer.getDistance(target) > 300) {
            if (repairer != null && target is MobileUnit && target.hitPoints < target.maxHitPoints() / 3) {
                target.board.goal = BeRepairedBy(repairer)
                return TaskResult(Resources(listOf(target)))
            }
            return TaskResult(status = TaskStatus.FAILED)
        }
        repairer.board.goal = Repairing(target as Mechanical)
        if (target is MobileUnit) {
            target.board.goal = BeRepairedBy(repairer)
            return TaskResult(Resources(listOf(repairer, target)))
        }
        return TaskResult(Resources(listOf(repairer)))
    }

    companion object {
        fun provideTasksTo(tasks: MutableSet<Task>) {
            (UnitQuery.myUnits.filter { it is Mechanical && it.hitPoints < it.maxHitPoints() && it.isCompleted }
                    - tasks.filterIsInstance(RepairTask::class.java).map { it.target })
                    .map { RepairTask(it) }
                    .forEach { tasks.add(it) }
        }
    }
}

