package org.styx.squad

import org.bk.ass.sim.Evaluator
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
        if (squad.fastEval == Evaluator.EVAL_NO_COMBAT) {
            return NodeStatus.RUNNING
        }
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty())
            return NodeStatus.RUNNING
        val targetSquad = Styx.squads.squads
                .maxBy { it.fastEval.value - it.enemies.size / 20.0 + it.mine.size / 50.0 }
                ?: return NodeStatus.RUNNING
        if (targetSquad.mine.isEmpty()) {
            attackerLock.units.forEach { attacker ->
                val target = TargetEvaluator.bestTarget(attacker, Styx.units.enemy, 0.3) ?: return@forEach
                BasicActions.attack(attacker, target)
            }
            return NodeStatus.RUNNING
        }

        squad.task = "Back Off"
        val bestUnit = targetSquad.mine.filter { !it.flying }.minBy { it.distanceTo(targetSquad.myCenter) }
                ?: targetSquad.mine.minBy { it.distanceTo(targetSquad.myCenter) }!!
        Styx.diag.log("Squad backing off ${squad.name} (${attackerLock.units.size} units) to $bestUnit")
        attackerLock.units.forEach { a ->
            when {
                squad.enemies.any { it.inAttackRange(a, 384) } -> {
                    if (a.flying) {
                        val force = Potential.reach(a, targetSquad.myCenter) +
                                Potential.avoidDanger(a, 64) * 0.5
                        Potential.apply(a, force)
                    } else {
                        BasicActions.follow(a, bestUnit)
                    }
                }
                else ->
                    squad.mine.filter { !it.flying }
                            .minBy { it.distanceTo(squad.myCenter) }
                            ?.let {
                                BasicActions.follow(a, it)
                            }
            }
        }
        return NodeStatus.RUNNING
    }
}