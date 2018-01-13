package org.fttbot.task

import org.fttbot.behavior.BeRepairedBy
import org.fttbot.behavior.Repair
import org.fttbot.info.UnitQuery
import org.fttbot.info.board
import org.fttbot.info.isMyUnit
import org.openbw.bwapi4j.unit.*
import kotlin.math.sqrt

interface Task {
    companion object {
        var repairTaskScale = 1.0

        fun step() {
            val tasks = RepairTask.generate().toMutableSet()
            val availableUnits = UnitQuery.myUnits.toMutableList()

            do {
                val task = tasks.maxBy { it.utility }
                if (task != null) {
                    tasks.remove(task)
                    val usedUnits = task.run(availableUnits)
                    if (!availableUnits.containsAll(usedUnits)) throw IllegalStateException()
                    availableUnits.removeAll(usedUnits)
                }
            } while (task != null)
        }
    }

    val utility : Double
    fun run(units: List<PlayerUnit>) : List<PlayerUnit>
}

class RepairTask(val target: PlayerUnit) : Task {
    private var chosenWorker : SCV? = null

    override val utility: Double get() = (1 - target.hitPoints * target.hitPoints / target.maxHitPoints().toDouble() / target.maxHitPoints()) * target.board.importance * Task.repairTaskScale

    override fun run(units: List<PlayerUnit>): List<PlayerUnit> {
        var repairer = chosenWorker
        if (repairer == null || !units.contains(repairer)) {
            repairer = units.filterIsInstance(SCV::class.java).filter { it != target }.minBy { it.getDistance(target) }
        } else {
            chosenWorker = repairer
            return listOf(repairer, target)
        }
        chosenWorker = repairer
        if (repairer == null || repairer.getDistance(target) > 300) return emptyList()
        repairer.board.goal = Repair(target)
        target.board.goal = BeRepairedBy(repairer)
        return listOf(repairer, target)
    }

    companion object {
        fun generate() : List<Task> =
                UnitQuery.myUnits.filter { it is Mechanical && it.hitPoints < it.maxHitPoints() && it.isCompleted}
                        .map { RepairTask(it) }
    }
}