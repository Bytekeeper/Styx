package org.styx.squad

import org.bk.ass.sim.Evaluator
import org.styx.*
import org.styx.Styx.bases
import org.styx.Styx.squads
import org.styx.action.BasicActions

class SeekCombatSquad(private val squad: Squad) : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { squad.mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun tick(): NodeStatus {
        if (squad.fastEval != Evaluator.EVAL_NO_COMBAT)
            return NodeStatus.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty())
            return NodeStatus.RUNNING
        val attackers = attackerLock.units
        val candidates = squads.squads.mapNotNull { c ->
            if (c.enemies.isEmpty())
                null
            else {
                val myAgents = (c.mine + attackers).distinct().filter { it.isCombatRelevant && it.unitType.canMove() }.map { it.agent() }
                val enemies = (squad.enemies + c.enemies).distinct().map { it.agent() }
                val evaluationResult = Styx.evaluator.evaluate(myAgents, enemies)
                if (evaluationResult != Evaluator.EVAL_NO_COMBAT)
                    c to evaluationResult.value
                else
                    null
            }
        }
        val targetCandidate = bestSquadToSupport(squad, candidates)
                ?: desperateAttemptToWinSquad(candidates)
        if (targetCandidate != null && targetCandidate.first != squad) {
//            Styx.diag.log("Squad seeking $squad - $targetCandidate")
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

    private fun bestSquadToSupport(squad: Squad, candidates: List<Pair<Squad, Double>>) =
            candidates.minBy { (s, eval) ->
                bases.enemyBases.map { it.center.getDistance(s.center) }.min(0.0) / 3000.0 +
                        s.center.getDistance(squad.center) / 3000.0 +
                        (eval - 0.6) * (s.enemies.count { it.isCombatRelevant } + s.enemies.size / 15.0) +
                        (eval - 0.55) * (s.mine.count { !it.isCombatRelevant } * 12
                        )
            }
}