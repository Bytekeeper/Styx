package org.styx.squad

import org.styx.BTNode
import org.styx.NodeStatus
import org.styx.Styx
import org.styx.UnitLocks
import org.styx.action.BasicActions

class SeekCombatSquad(private val squad: Squad) : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { squad.mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun tick(): NodeStatus {
        // 0.5 - No damage to either side; != 0.5 - Flee due to combat sim
        if (squad.fastEval != 0.5)
            return NodeStatus.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty())
            return NodeStatus.RUNNING
        val attackers = attackerLock.units
        val enemySquads = Styx.squads.squads.filter { it.enemies.isNotEmpty() }
        val candidates = enemySquads.map {
            val myAgents = (attackers + it.mine).distinct().filter { it.unitType.canAttack() && it.unitType.canMove() }.map { it.agent() }
            val enemies = (squad.enemies + it.enemies).distinct().map { it.agent() }
            it to Styx.evaluator.evaluate(myAgents, enemies)
        }
        val targetSquad = bestSquadToSupport(candidates)
                ?: desperateAttemptToWinSquad(candidates)
        if (targetSquad != null) {
            squad.task = "Attack"
            val targetPosition = targetSquad.mine.filter { !it.flying }.minBy { it.distanceTo(targetSquad.myCenter) }?.position
                    ?: targetSquad.enemies.filter { !it.flying }.minBy { it.distanceTo(targetSquad.center) }?.position
            attackers.forEach { attacker ->
                if (targetPosition != null && attacker.threats.isEmpty()) {
//                    val force = Potential.groundAttract(attacker, targetPosition) +
//                            Potential.keepGroundCompany(attacker, 32) * 0.2
//                    Potential.apply(attacker, force)
                    BasicActions.move(attacker, targetPosition)
                } else {
                    Styx.resources.releaseUnit(attacker)
                }
            }
        } else {
            attackerLock.release()
        }
        return NodeStatus.RUNNING
    }

    private fun desperateAttemptToWinSquad(candidates: List<Pair<Squad, Double>>) =
            if (Styx.balance.direSituation)
                candidates.maxBy { it.second }?.let { if (it.second > 0.3) it.first else null }
            else
                null

    private fun bestSquadToSupport(candidates: List<Pair<Squad, Double>>) =
            candidates.filter { it.first != squad && it.second > 0.6 }.maxBy { it.second }?.first
}