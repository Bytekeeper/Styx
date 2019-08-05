package org.styx.squad

import org.styx.*
import org.styx.action.BasicActions

class SquadBoard {
    var fastEval: Double = 0.5
    var mine = emptyList<SUnit>()
    var enemies = emptyList<SUnit>()
}

class SquadFightLocal : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { board<SquadBoard>().mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun tick(): TickResult {
        val enemies = board<SquadBoard>().enemies
        if (enemies.isEmpty()) return TickResult.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty()) return TickResult.RUNNING
        val attackers = attackerLock.units

        attackers.forEach { attacker ->
            val target = TargetEvaluator.bestTarget(attacker, enemies) ?: return@forEach
            BasicActions.attack(attacker, target)
        }
        return TickResult.RUNNING
    }
}

class SquadAttack : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { board<SquadBoard>().mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun tick(): TickResult {
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty()) return TickResult.RUNNING
        val attackers = attackerLock.units

        attackers.forEach { attacker ->
            val target = TargetEvaluator.bestTarget(attacker, Styx.units.enemy) ?: return@forEach
            BasicActions.attack(attacker, target)
        }
        return TickResult.RUNNING
    }
}

