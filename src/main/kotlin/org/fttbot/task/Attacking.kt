package org.fttbot.task

import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.PlayerUnit
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

    private val attackTasks = ParallelTask(ManagedTaskProvider({ attackers }, {
        val attack = Attack(it)
        SimpleTask {
            if (!attack.target!!.exists()) {
                val potentials = targets.filter { it.exists() }
                if (!potentials.isEmpty()) {
                    attack.target = potentials[rng.nextInt(potentials.size)]
                } else
                    return@SimpleTask TaskStatus.RUNNING
            }
            attack.process()
            TaskStatus.RUNNING
        }
    }))

    override fun processInternal(): TaskStatus = attackTasks.process()
}