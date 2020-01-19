package org.styx.squad

import org.bk.ass.bt.TreeNode
import org.bk.ass.sim.Evaluator
import org.styx.*
import org.styx.action.BasicActions
import org.styx.micro.Potential

class SquadBackOff(private val squad: SquadBoard) : TreeNode() {
    private val attackerLock = UnitLocks {
        Styx.resources.availableUnits.filter {
            squad.mine.contains(it) &&
                    !it.unitType.isWorker &&
                    it.unitType.canMove() &&
                    it.unitType.canAttack()
        }
    }

    override fun exec() {
        running()
        if (squad.fastEval == Evaluator.EVAL_NO_COMBAT) {
            return
        }
        attackerLock.reacquire()
        if (attackerLock.item.isEmpty())
            return
        val targetSquad = Styx.squads.squads
                .maxBy { it.mine.count { it.unitType.isBuilding } }
                ?: return
        if (targetSquad.mine.isEmpty()) {
            attackerLock.item.forEach { attacker ->
                val target = TargetEvaluator.bestTarget(attacker, Styx.units.enemy, 0.3) ?: return@forEach
                BasicActions.attack(attacker, target)
            }
            return
        }

        squad.task = "Back Off"
        val bestUnit = targetSquad.mine.filter { !it.flying }.minBy { it.distanceTo(targetSquad.myCenter) }
                ?: targetSquad.mine.minBy { it.distanceTo(targetSquad.myCenter) }!!
        Styx.diag.log("Squad backing off ${squad.name} (${attackerLock.item.size} units) to $bestUnit")
        attackerLock.item.toList()
                .forEach { a ->
                    when {
                        a.safe -> attackerLock.releaseItem(a)
                        a.flying || a.threats.any { it.inAttackRange(a, 32) } -> {
                            val force = Potential.reach(a, targetSquad.myCenter) * 0.7 +
                                    Potential.avoidDanger(a, 64)
                            Potential.apply(a, force)
                        }
                        else -> {
                            BasicActions.follow(a, bestUnit)
                        }
                    }
                }
    }
}