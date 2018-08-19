package org.fttbot.task

import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.estimation.SimUnit.Companion.of
import org.fttbot.info.*
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker
import java.util.*

class Attack(val unit: PlayerUnit, var target: PlayerUnit? = null) : Task() {
    override val utility: Double = 1.0

    override fun processInternal(): TaskStatus {
        if (target == null || !target!!.exists()) return TaskStatus.FAILED
        if (unit.orderTarget != target) {
            (unit as Attacker).attack(target)
        }
        return TaskStatus.RUNNING
    }

}

class CoordinatedAttack(var attackers: List<PlayerUnit> = listOf(), var targets: List<PlayerUnit> = listOf()) : Task() {
    private val rng = SplittableRandom()
    override val utility: Double = 1.0

    private val attackerTasks = ManagedTaskProvider({ attackers }, {
        val attack = Attack(it)
        SimpleTask {
            val target = targets.filter { e -> e.exists() && (it as Attacker).getWeaponAgainst(e).type() != WeaponType.None && e.isDetected }
                    .minBy { e -> it.getDistance(e) }
                    ?: return@SimpleTask TaskStatus.RUNNING
            attack.target = target
            attack.process()
            TaskStatus.RUNNING
        }
    })

    override fun processInternal(): TaskStatus = processAll(attackerTasks)

    companion object : TaskProvider {
        private val attack = CoordinatedAttack()
        private val tasks = listOf(attack.repeat())
        override fun invoke(): List<Task> {
            attack.attackers = UnitQuery.myUnits.filter { it is Attacker && it !is Worker }
            attack.targets = UnitQuery.enemyUnits
            return tasks
        }
    }
}

class CoordinateClusters() : Task() {
    override val utility: Double
        get() = 1.0

    override fun processInternal(): TaskStatus {
        Cluster.clusters.forEach {
            val calc = CombatEval.probabilityToWin(it.units.filter { it.isMyUnit }.map(SimUnit.Companion::of),
                    it.units.filter { it.isEnemyUnit }.map(SimUnit.Companion::of))
            if (it.units.count { it.isEnemyUnit && it is Attacker } > 0) {
                val a = calc
            }
        }
        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {
        private val coordinateClusters: Task = CoordinateClusters()

        override fun invoke(): List<Task> = listOf(coordinateClusters)
    }
}