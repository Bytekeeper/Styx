package org.styx.squad

import org.bk.ass.sim.Evaluator
import org.styx.*
import org.styx.micro.Potential

class ClusterTogether(private val squad: Squad) : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { squad.mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun tick(): NodeStatus {
        if (squad.fastEval != Evaluator.EVAL_NO_COMBAT)
            return NodeStatus.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty())
            return NodeStatus.RUNNING
        val attackers = attackerLock.units
        val targetPosition = squad.mine.filter { !it.flying }.minBy { it.distanceTo(squad.myCenter) }?.position
                ?: return NodeStatus.RUNNING
        attackers.forEach { attacker ->
            if (attacker.threats.isEmpty()) {
                val force = Potential.reach(attacker, targetPosition) +
                        Potential.collisionRepulsion(attacker) * 0.3 +
                        Potential.keepGroundCompany(attacker, 32) * 0.2
                Potential.apply(attacker, force)
            } else {
                Styx.resources.releaseUnit(attacker)
            }
        }
        return NodeStatus.RUNNING
    }
}