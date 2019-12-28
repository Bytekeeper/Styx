package org.styx.global

import org.styx.LazyOnFrame
import org.styx.Styx

class Balance {
    val globalFastEval by LazyOnFrame {
        val myAgents = Styx.units.mine.map { it.agent() }
        val enemyAgents = Styx.units.enemy.map { it.agent() }
        Styx.evaluator.evaluate(myAgents, enemyAgents)
    }
    val direSituation get() = globalFastEval.value < 0.2
}