package org.styx.global

import bwapi.WeaponType
import org.styx.LazyOnFrame
import org.styx.Styx

class Balance {
    val evalMyMobileVsAllEnemy by LazyOnFrame {
        val myAgents = Styx.units.mine.filter { it.unitType.canMove() }.map { it.agent() }
        val enemyAgents = Styx.units.enemy.map { it.agent() }
        Styx.evaluator.evaluate(myAgents, enemyAgents)
    }
    val evalMyCombatVsMobileGroundEnemy by LazyOnFrame {
        val myAgents = Styx.units.mine.filter { it.unitType.groundWeapon() != WeaponType.None }.map { it.agent() }
        val enemyAgents = Styx.units.enemy.filter { !it.flying && it.unitType.canMove() }.map { it.agent() }
        Styx.evaluator.evaluate(myAgents, enemyAgents)
    }
}