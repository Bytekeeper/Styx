package org.styx.squad

import bwapi.UnitType
import org.bk.ass.bt.Distributor
import org.bk.ass.bt.Parallel
import org.bk.ass.bt.TreeNode
import org.bk.ass.sim.Agent
import org.bk.ass.sim.IntEvaluation
import org.styx.*
import org.styx.Styx.diag
import org.styx.Styx.evaluator
import org.styx.Styx.fleeSim
import org.styx.Styx.sim
import org.styx.Styx.simFS3
import org.styx.Styx.units
import org.styx.micro.Attack
import org.styx.micro.AvoidCombat
import org.styx.micro.Stop

data class SimResult(val lostUnits: List<SUnit>, val eval: IntEvaluation)

class LocalCombat(private val squad: SquadBoard) : TreeNode() {
    private var workersToUse = 0
    private var lastAttackHysteresis = 0
    private var combatMoves = listOf<CombatMove>()
    private val combatDispatch = Distributor(
            Parallel.Policy.SEQUENCE,
            { combatMoves },
            { combatMoveToTree(it) })

    private val attackerLock = UnitLocks {
        val candidates = UnitReservation.availableItems
                .filter { squad.mine.contains(it) }
        val combatUnits = candidates.filter {
            it.isCombatRelevant && it.isCompleted
        }
        val workersForCombat = candidates.filter { it.unitType.isWorker }
                .sortedByDescending { it.hitPoints * (if (it.gathering || it.carrying) 0.7 else 1.0) }
                .take(workersToUse)
        combatUnits + workersForCombat
    }

    override fun exec() {
        diag.log(squad.toString())
        running()
        val enemies = squad.enemies
        val attackHysteresis = lastAttackHysteresis
        lastAttackHysteresis = 0
        val buildingsAreSave = squad.mine.none { b ->
            b.unitType.isBuilding && !b.unitType.canAttack()
                    && squad.enemies.any { it.inAttackRange(b, 64) && (it.target == b || it.orderTarget == b) }
        }
        if (enemies.isEmpty() && buildingsAreSave) {
            return
        }
        attackerLock.reacquire()
        val maxUseableWorkers = squad.myWorkers.size
        if (attackerLock.item.isEmpty() && maxUseableWorkers == 0) {
            workersToUse = 0
            return
        }
        val attackers = attackerLock.item

        val agentsBeforeCombat = prepareCombatSim()
        val bestEval = evaluator.optimizeEval(attackers.map { unitMapper(it) }, sim.agentsB)
        if (bestEval.agents.isNotEmpty() && bestEval.agents.size < attackers.size) {
            val toDismiss = attackers - bestEval.agents.map { it.userObject as SUnit }
            diag.log("Dismissing $toDismiss to improve eval from ${squad.fastEval} to ${bestEval.eval}")
            attackerLock.releasePartially(toDismiss)
        }
        val beforeCombat = sim.evalToInt(agentValueForPlayer)
//        val valuesBeforeCombat = agentsBeforeCombat.map { it to agentValueForPlayer.applyAsInt(it) }.toMap()
        var afterCombat = simulateCombat(agentsBeforeCombat, Config.shortSimHorizon)
        var scoreAfterCombat = afterCombat.eval.delta() * 4
        afterCombat = simulateCombat(emptyList(), Config.shortSimHorizon)
        scoreAfterCombat += afterCombat.eval.delta() * 2
        afterCombat = simulateCombat(emptyList(), Config.shortSimHorizon)
        scoreAfterCombat += afterCombat.eval.delta()
        scoreAfterCombat /= (4 + 2 + 1)

        val afterFlee = simulateFleeing()

        val scoreAfterFleeing = afterFlee.eval.delta()
        val shouldFlee = scoreAfterFleeing > scoreAfterCombat + attackHysteresis + maxUseableWorkers * 4
                && !shouldSnipePylon(enemies)

        diag.log("COMBAT SCORE ${squad.name} after combat: $scoreAfterCombat; after fleeing: $scoreAfterFleeing; attackHysteresis $attackHysteresis")
        diag.log("SIM RESULTS ${squad.name} after combat: ${sim.agentsA} | ${sim.agentsB}; after flee ${fleeSim.agentsA} | ${fleeSim.agentsB}")

        var aggressiveness = 1.0
        if (buildingsAreSave && shouldFlee) {
            diag.log("RETREAT ${squad.name} $shouldFlee")
            workersToUse = 0
            lastAttackHysteresis = 0

            val attackersToKeep = sim.agentsA.mapNotNull {
                val unit = it.userObject as SUnit
                if (it.attackCounter > 0 && unit.safe)
                    unit
                else
                    null
            }

            val lostUnits = afterFlee.lostUnits.filter { lu ->
                units.enemy.nearest(lu.x, lu.y, 400) { it.inAttackRange(lu, -32) } != null
            }

            // Keep units that'd die anyway or are not in danger
            attackerLock.releasePartially(attackers - lostUnits - attackersToKeep)
            if (attackers.isEmpty())
                return
            else {
                diag.log("Will lose ${afterFlee.lostUnits} anyways, keep attacking. Will also not lose $attackers either way, so keep them going too")
            }
            aggressiveness = 0.5
        } else {
            diag.log("ATTACK ${squad.name}")
            squad.task = "Fight"

            val potentialAttackers = attackers.map(unitMapper).toMutableList()
            workersToUse = attackers.count { it.unitType.isWorker }
            val enemyAgents = enemies.map(unitMapper)
            if (buildingsAreSave) workersToUse = 0
            else {
                while (workersToUse < maxUseableWorkers && evaluator.evaluate(potentialAttackers, enemyAgents).value <= 0.5) {
                    potentialAttackers += squad.myWorkers.random().agent()
                    workersToUse++
                }
                if (evaluator.evaluate(potentialAttackers, enemyAgents).value <= 0.3) {
                    workersToUse = 0
                }
                while (workersToUse > 0) {
                    val indexOfFirstWorker = potentialAttackers.indexOfFirst {
                        val maybeWorker = it.userObject as SUnit
                        maybeWorker.unitType.isWorker
                    }
                    if (indexOfFirstWorker >= 0) {
                        if (evaluator.evaluate(potentialAttackers - potentialAttackers[indexOfFirstWorker], enemyAgents).value > 0.5) {
                            workersToUse--
                            potentialAttackers.removeAt(indexOfFirstWorker)
                        } else {
                            break
                        }
                    } else {
                        break
                    }

                }
            }

            lastAttackHysteresis = (beforeCombat.evalA * Config.attackerHysteresisFactor).toInt()
        }

        if (attackers.isNotEmpty()) {
            performAttack(attackers, enemies, aggressiveness)
        }

        return
    }

    private fun shouldSnipePylon(enemies: List<SUnit>): Boolean {
        return if (enemies.count { it.unitType == UnitType.Protoss_Pylon } == 1 && enemies.count { it.unitType == UnitType.Protoss_Photon_Cannon } > 2) {
            simFS3.reset()
            squad.mine
                    .forEach { simFS3.addAgentA(unitMapper(it)) }
            squad.enemies
                    .forEach { simFS3.addAgentB(unitMapper(it)) }
            simFS3.agentsB.forEach {
                val unit = (it.userObject as SUnit)
                if (unit.unitType != UnitType.Protoss_Pylon) {
                    it.setAttackTargetPriority(Agent.TargetingPriority.LOW)
                } else {
                    it.setAttackTargetPriority(Agent.TargetingPriority.HIGHEST)
                }
            }
            simFS3.simulate(Config.veryLongSimHorizon)
            simFS3.agentsB.none { (it.userObject as SUnit).unitType == UnitType.Protoss_Pylon }
        } else
            false
    }

    private fun performAttack(attackers: List<SUnit>, enemies: List<SUnit>, aggressiveness: Double) {
        combatMoves = TargetEvaluator.bestCombatMoves(attackers, enemies, aggressiveness)
                .mapNotNull {
                    if (it is Disengage) {
                        attackerLock.releaseItem(it.unit)
                        null
                    } else
                        it
                }
//        diag.log("Squad combat moves ${squad.name} $combatMoves")
        combatDispatch.exec()
    }

    private fun combatMoveToTree(move: CombatMove): TreeNode =
            when (move) {
                is AttackMove -> Attack(move.unit, move.enemy)
                is WaitMove -> Stop(move.unit)
                is StayBack -> AvoidCombat { move.unit }
                else -> error("Invalid move: $move")
            }

    private fun simulateCombat(agentsBeforeCombat: List<Agent>, frames: Int): SimResult {
        sim.simulate(frames)
        val lostUnits = agentsBeforeCombat.filter { it.health <= 0 }.map { it.userObject as SUnit }
        val eval = sim.evalToInt(agentValueForPlayer)
        return SimResult(lostUnits, eval)
    }

    private fun prepareCombatSim(): List<Agent> {
        sim.reset()
        val agentsBeforeCombat = squad.mine.map(unitMapper)
        agentsBeforeCombat
                .forEach { sim.addAgentA(it) }
        squad.enemies
                .forEach {
                    sim.addAgentB(unitMapper(it))
                }
        val allAgents = sim.agentsA + sim.agentsB
        val units = allAgents.map {
            val u = it.userObject as SUnit
            u to it
        }.toMap()
        for ((u, a) in units) {
            if (u.unitType == UnitType.Protoss_Carrier) {
                a.setInterceptors(
                        u.unit.interceptors
                                .mapNotNull { units[SUnit.forUnit(it)] }
                )
            }
            val target = u.target
            val targetAgent = units[target]
            if (target != null && targetAgent != null) {
                if (u.player.isAlly(target.player)) {
                    a.setRestoreTarget(targetAgent)
                } else {
                    a.setAttackTarget(targetAgent)
                }
            }
        }
        return agentsBeforeCombat
    }

    private fun simulateFleeing(): SimResult {
        fleeSim.reset()
        val agentsBeforeCombat = squad.mine.map {
            val agent = unitMapper(it)
            if (!it.flying)
                agent.setSpeedFactor(0.9f)
            agent
        }
        agentsBeforeCombat
                .forEach { fleeSim.addAgentA(it) }
        squad.enemies.map(unitMapper)
                .forEach { fleeSim.addAgentB(it) }
        fleeSim.simulate(Config.mediumSimHorizon)
        val lostUnits = agentsBeforeCombat.filter { it.health <= 0 }.map { it.userObject as SUnit }
        val eval = fleeSim.evalToInt(agentValueForPlayer)
        return SimResult(lostUnits, eval)
    }

    private val unitMapper = { a: SUnit -> unitToAgentMapper(a, attackerLock.item) }
}
