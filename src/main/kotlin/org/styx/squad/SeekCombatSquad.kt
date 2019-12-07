package org.styx.squad

import org.styx.*
import org.styx.Styx.squads
import org.styx.micro.Potential

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
        val candidates = squads.squads.map {
            val myAgents = (attackers + it.mine).distinct().filter { it.unitType.canAttack() && it.unitType.canMove() }.map { it.agent() }
            val enemies = (squad.enemies + it.enemies).distinct().map { it.agent() }
            it to Styx.evaluator.evaluate(myAgents, enemies)
        }
        val targetCandidate = bestSquadToSupport(candidates)
                ?: desperateAttemptToWinSquad(candidates)
        if (targetCandidate != null) {
            Styx.diag.log("Squad seeking $squad - $targetCandidate")
            squad.task = "Attack"
            val targetSquad = targetCandidate.first
            val targetPosition = targetSquad.mine.filter { !it.flying }.minBy { it.distanceTo(targetSquad.myCenter) }?.position
                    ?: targetSquad.enemies.filter { !it.flying }.minBy { it.distanceTo(targetSquad.center) }?.position
            attackers.forEach { attacker ->
                if (targetPosition != null && attacker.threats.isEmpty()) {
                    val force = Potential.reach(attacker, targetPosition) +
                            Potential.collisionRepulsion(attacker) * 0.3 +
                            Potential.keepGroundCompany(attacker, 32) * 0.2
                    Potential.apply(attacker, force)
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
            candidates.sortedBy { it.second }
                    .firstOrNull {
                        it.first.shouldBeDefended && it.first.enemies.isNotEmpty() ||
                                it.second > it.first.fastEval && it.first.enemies.size > squad.enemies.size ||
                                it.second == 0.5 && it.first.mine.count { u -> u.isCombatRelevant } > squad.mine.count { u -> u.isCombatRelevant }
                    }
                    ?.let { it.first to it.second }
}