package org.styx.squad

import org.bk.ass.bt.TreeNode
import org.bk.ass.sim.Evaluator
import org.styx.*
import org.styx.Styx.bases
import org.styx.Styx.squads
import org.styx.action.BasicActions

class SeekCombatSquad(private val squad: SquadBoard) : TreeNode() {
    private val attackerLock = UnitLocks { UnitReservation.availableItems.filter { squad.mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun exec() {
        running()
        if (squad.fastEval != Evaluator.EVAL_NO_COMBAT)
            return
        attackerLock.reacquire()
        if (attackerLock.item.isEmpty())
            return
        val attackers = attackerLock.item
        val targetSquad = squads.squads.maxBy { c ->
            if (c.enemies.isEmpty())
                0.0
            else {
                val myAdditionals = attackers.filter { it.unitType.canMove() }
                val enemyAdditionals = squad.enemies.filter { it.unitType.canMove() }
                c.stockUpUtility(myAdditionals, enemyAdditionals)
            }
        }

        if (targetSquad != null && targetSquad != squad) {
//            Styx.diag.log("Squad seeking $squad - $targetCandidate")
            squad.task = "Attack"
            val targetGroundPosition = targetSquad.mine.filter { !it.flying }.minBy { it.distanceTo(targetSquad.myCenter) }?.position
                    ?: targetSquad.enemies.filter { !it.flying }.minBy { it.distanceTo(targetSquad.center) }?.position
            val targetAirPosition = targetSquad.all.minBy { it.distanceTo(targetSquad.center) }!!.position
            attackers.forEach { attacker ->
                if (targetGroundPosition != null && attacker.threats.isEmpty()) {
                    if (attacker.flying) {
                        BasicActions.move(attacker, targetAirPosition)
                    } else {
                        BasicActions.move(attacker, targetGroundPosition)
                    }
                } else {
                    UnitReservation.release(this, attacker)
                }
            }
        } else {
            attackerLock.release()
        }
        return
    }
}