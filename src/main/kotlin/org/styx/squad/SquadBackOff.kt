package org.styx.squad

import org.styx.*
import org.styx.action.BasicActions

class SquadBackOff(private val squad: Squad) : BTNode() {
    private val attackerLock = UnitLocks {
        Styx.resources.availableUnits.filter {
            squad.mine.contains(it) &&
                    !it.unitType.isWorker &&
                    it.unitType.canMove() &&
                    it.unitType.canAttack()
        }
    }

    override fun tick(): NodeStatus {
        // 0.5 - no need to run away
        if (squad.fastEval == 0.5) {
            return NodeStatus.RUNNING
        }
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty())
            return NodeStatus.RUNNING
        val targetSquad = Styx.squads.squads
                .maxBy { it.fastEval - it.enemies.size / 7.0 + it.mine.size / 7.0 + it.center.getDistance(squad.center) / 2500.0 }
                ?: return NodeStatus.RUNNING
        if (targetSquad.mine.isEmpty()) {
            attackerLock.units.forEach { attacker ->
                val target = TargetEvaluator.bestTarget(attacker, Styx.units.enemy) ?: return@forEach
                BasicActions.attack(attacker, target)
            }
            return NodeStatus.RUNNING
        }
        squad.task = "Back Off"
        val bestUnit = targetSquad.mine.filter { !it.flying }.minBy { it.distanceTo(targetSquad.myCenter) }
                ?: targetSquad.mine.minBy { it.distanceTo(targetSquad.myCenter) }!!
        attackerLock.units.forEach { a ->
            if (squad.enemies.any { it.inAttackRange(a, 192f) } &&
                    a.distanceTo(bestUnit) > 32) {
                BasicActions.follow(a, bestUnit)
//                val force = Potential.groundAttract(a, targetSquad.myCenter)
//                Potential.apply(a, force)
            } else if (a.moving && a.distanceTo(bestUnit) < 96) {
                a.stop()
            }
        }
        return NodeStatus.RUNNING
    }
}