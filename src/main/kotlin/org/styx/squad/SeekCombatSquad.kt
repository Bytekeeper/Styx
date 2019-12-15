package org.styx.squad

import org.styx.*
import org.styx.Styx.squads
import org.styx.action.BasicActions
import org.styx.micro.Potential
import kotlin.math.abs

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
        val candidates = squads.squads.map { c ->
            val myAgents = (c.mine + attackers).distinct().filter { it.isCombatRelevant && it.unitType.canMove() }.map { it.agent() }
            val enemies = (squad.enemies + c.enemies).distinct().map { it.agent() }
            c to Styx.evaluator.evaluate(myAgents, enemies)
        }
        val targetCandidate = bestSquadToSupport(candidates)
                ?: desperateAttemptToWinSquad(candidates)
        if (targetCandidate != null && targetCandidate.first != squad) {
            Styx.diag.log("Squad seeking $squad - $targetCandidate")
            squad.task = "Attack"
            val targetSquad = targetCandidate.first
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
                candidates.maxBy { it.second }?.let { if (it.second > 0.3) it.first to 0.5 else null }
            else
                null

    private fun bestSquadToSupport(candidates: List<Pair<Squad, Double>>) =
            candidates.filter { it.first.enemies.isNotEmpty() && it.second != 0.5 && it.first.fastEval < it.second }
                    .maxBy { (s, eval) ->
                        abs((eval - 0.6) * (s.enemies.count { it.isCombatRelevant } + s.enemies.size / 10.0)) +
                                (0.7 - eval) * (s.mine.count { !it.isCombatRelevant } * 3)
                    }
}