package org.styx.squad

import org.bk.ass.bt.TreeNode
import org.bk.ass.sim.Evaluator
import org.styx.*
import org.styx.micro.Force

class ClusterTogether(private val squad: SquadBoard) : TreeNode() {
    private val attackerLock = UnitLocks { UnitReservation.availableItems.filter { squad.mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun exec() {
        running()
        if (squad.fastEval != Evaluator.EVAL_NO_COMBAT)
            return
        attackerLock.reacquire()
        if (attackerLock.item.isEmpty())
            return
        val attackers = attackerLock.item
        val targetPosition = squad.mine.filter { !it.flying }.minBy { it.distanceTo(squad.myCenter) }?.position
                ?: return
        attackers.forEach { attacker ->
            if (attacker.safe) {
                val force = Force().reach(attacker, targetPosition)
                        .collisionRepulsion(attacker, 0.3)
                        .keepGroundCompany(attacker, 32, 0.2)
                force.apply(attacker)
            } else {
                UnitReservation.release(this, attacker)
            }
        }
        return
    }
}