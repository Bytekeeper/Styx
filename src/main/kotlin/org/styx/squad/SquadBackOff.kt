package org.styx.squad

import org.styx.*
import org.styx.action.BasicActions
import org.styx.micro.Potential

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
                .maxBy { it.fastEval - it.enemies.size / 15.0 + it.mine.size / 10.0 }
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
            if (squad.enemies.any { it.inAttackRange(a, 32f) }) {
                val force = Potential.groundAttract(a, targetSquad.myCenter) +
                        Potential.avoidDanger(a, 96) * 0.3 +
                        Potential.collisionRepulsion(a) * 0.2
                Potential.apply(a, force)
            } else if (a.distanceTo(bestUnit) > 64) {
                BasicActions.follow(a, bestUnit)
            } else if (a.moving && a.distanceTo(bestUnit) < 96) {
                a.stop()
            }
        }
        return NodeStatus.RUNNING
    }
}