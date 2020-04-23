package org.styx.global

import bwapi.WeaponType
import org.styx.LazyOnFrame
import org.styx.Styx

class Balance {
    val globalFastEval by LazyOnFrame {
        val myAgents = Styx.units.mine.map { it.agent() }
        val enemyAgents = Styx.units.enemy.map { it.agent() }
        Styx.evaluator.evaluate(myAgents, enemyAgents)
    }
    val globalVsGroundEval by LazyOnFrame {
        val myAgents = Styx.units.mine.filter { it.unitType.groundWeapon() != WeaponType.None }.map { it.agent() }
        val enemyAgents = Styx.units.enemy.filter { !it.flying }.map { it.agent() }
        Styx.evaluator.evaluate(myAgents, enemyAgents)
    }
    val direSituation get() = globalFastEval.value < 0.2
}