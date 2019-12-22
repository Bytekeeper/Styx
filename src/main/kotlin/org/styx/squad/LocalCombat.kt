package org.styx.squad

import bwapi.UnitType
import org.bk.ass.sim.Agent
import org.bk.ass.sim.IntEvaluation
import org.styx.*
import org.styx.Styx.diag
import org.styx.Styx.fleeSim
import org.styx.Styx.ft
import org.styx.Styx.sim
import org.styx.Styx.simFS3
import org.styx.micro.Attack
import org.styx.micro.Stop
import kotlin.math.max
import kotlin.math.min

data class SimResult(val lostUnits: List<SUnit>, val eval: IntEvaluation)

class LocalCombat(private val squad: Squad) : BTNode() {
    private var workersToUse = 0
    private var lastAttackHysteresis = 0
    private var combatMoves = listOf<CombatMove>()
    private val combatDispatch = Dispatch(
            "Combat",
            { combatMoves },
            { combatMoveToTree(it) })

    private val attackerLock = UnitLocks {
        val candidates = Styx.resources.availableUnits
                .filter { squad.mine.contains(it) }
        val combatUnits = candidates.filter {
            it.isCombatRelevant
        }
        val workersForCombat = candidates.filter { it.unitType.isWorker }
                .sortedByDescending { it.hitPoints * (if (it.gathering || it.carrying) 0.7 else 1.0) }
                .take(workersToUse)
        combatUnits + workersForCombat
    }

    override fun tick(): NodeStatus {
        val enemies = squad.enemies
        val attackHysteresis = lastAttackHysteresis
        lastAttackHysteresis = 0
        val workersAndBuildingsAreSave = squad.mine.none { b ->
            (b.unitType.isBuilding || b.unitType.isWorker) && squad.enemies.any { it.inAttackRange(b, 64) }
        } || squad.mine.none { it.unitType.isBuilding }
        if (workersAndBuildingsAreSave) {
            workersToUse = 0
        }
        if (enemies.isEmpty() && workersAndBuildingsAreSave) {
            return NodeStatus.RUNNING
        }
        attackerLock.reacquire()
        val maxUseableWorkers = squad.myWorkers.size
        if (attackerLock.units.isEmpty() && maxUseableWorkers == 0) {
            workersToUse = 0
            return NodeStatus.RUNNING
        }
        val attackers = attackerLock.units

        val agentsBeforeCombat = prepareCombatSim()
        val beforeCombat = sim.evalToInt(agentValueForPlayer)
        val valuesBeforeCombat = agentsBeforeCombat.map { it to agentValueForPlayer.applyAsInt(it) }.toMap()
        val sims = (1..1).map { simulateCombat(agentsBeforeCombat, Config.simHorizon) }
        val afterCombat = sims.last()
        val afterFlee = simulateFleeing()

        val scoreAfterCombat = afterCombat.eval.delta()
        val scoreAfterFleeing = afterFlee.eval.delta()
        val shouldFlee = scoreAfterFleeing > scoreAfterCombat + attackHysteresis + maxUseableWorkers * 3
        diag.log("Squad combat score ${squad.name} after combat: $scoreAfterCombat; after fleeing: $scoreAfterFleeing")

        var aggressiveness = 1.0
        if (workersAndBuildingsAreSave && shouldFlee) {
            diag.log("Squad retreat ${squad.name} $shouldFlee - ${sim.agentsA} | ${sim.agentsB} - when fleeing ${fleeSim.agentsA} | ${fleeSim.agentsB}")
            if (enemies.any { !it.flying }) {
                workersToUse = min(maxUseableWorkers, workersToUse + 1)
            }
            lastAttackHysteresis = -enemies.size * Config.attackerHysteresisValue

            val valuesAfterCombat = sim.agentsA.map { it to agentValueForPlayer.applyAsInt(it) }
            val attackersToKeep = valuesAfterCombat.filter { (a, v) ->
                a.attackCounter > 0 &&
                        v * 1.1 > (valuesBeforeCombat[a] ?: error("More units after combat? Not really..."))
            }.map { it.first.userObject as SUnit }

            val (ticklersLost, _) = simulateTicklers(attackersToKeep, 128)

            squad.fastEval = afterCombat.eval.evalA / max(beforeCombat.evalA.toDouble(), 0.001)

            // Keep units that'd die anyway
            attackerLock.releaseUnits(attackers - afterFlee.lostUnits - attackersToKeep + ticklersLost)
            if (attackers.isEmpty())
                return NodeStatus.RUNNING
            else {
                diag.log("Will lose ${afterFlee.lostUnits} anyways, keep attacking. Will also not lose $attackers either way, so keep them going too")
            }
            aggressiveness = 0.5
        } else {
            squad.task = "Fight"

            if (scoreAfterCombat > scoreAfterFleeing + Config.attackerHysteresisValue / 2)
                workersToUse = max(0, workersToUse - 1)
            else if (scoreAfterFleeing > scoreAfterCombat && workersToUse < maxUseableWorkers && enemies.any { !it.flying }) {
                workersToUse = min(maxUseableWorkers, workersToUse + 1)
            }
            diag.log("Squad fight ${squad.name} $scoreAfterCombat $scoreAfterFleeing")
        }

        lastAttackHysteresis = attackers.size * Config.attackerHysteresisValue

        if (attackers.isNotEmpty()) {
            performAttack(attackers, enemies, aggressiveness)
        }

        return NodeStatus.RUNNING
    }

    private fun performAttack(attackers: List<SUnit>, enemies: List<SUnit>, aggressiveness: Double) {
        combatMoves = ft("best combat moves") { TargetEvaluator.bestCombatMoves(attackers, enemies, aggressiveness) }
                .mapNotNull {
                    if (it is Disengage) {
                        attackerLock.releaseUnit(it.unit)
                        null
                    } else
                        it
                }
//        diag.log("Squad combat moves ${squad.name} $combatMoves")
        combatDispatch()
    }

    private fun combatMoveToTree(move: CombatMove): SimpleNode =
            when (move) {
                is AttackMove -> Attack(move.unit, move.enemy)
                is WaitMove -> Stop(move.unit)
                else -> error("Invalid move: $move")
            }

    private val unitToAgentMapper = { it: SUnit ->
        val agent = it.agent()
        if (it.enemyUnit && (it.gathering ||
                        it.unitType.isWorker && !it.visible) || (it.myUnit && !attackerLock.units.contains(it)) ||
                !it.hasPower)
            agent.setCooldown(Config.simHorizon)
        if (!it.unitType.canAttack() && it.unitType != UnitType.Terran_Bunker)
            agent.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        agent
    }

    private fun simulateCombat(agentsBeforeCombat: List<Agent>, frames: Int): SimResult {
        ft("combat sim") { sim.simulate(frames) }
        val lostUnits = agentsBeforeCombat.filter { it.health <= 0 }.map { it.userObject as SUnit }
        val eval = sim.evalToInt(agentValueForPlayer)
        return SimResult(lostUnits, eval)
    }

    private fun prepareCombatSim(): List<Agent> {
        sim.reset()
        val agentsBeforeCombat = squad.mine.map(unitToAgentMapper)
        agentsBeforeCombat
                .forEach { sim.addAgentA(it) }
        squad.enemies
                .forEach { sim.addAgentB(unitToAgentMapper(it)) }
        return agentsBeforeCombat
    }

    private fun simulateFleeing(): SimResult {
        fleeSim.reset()
        val agentsBeforeCombat = squad.mine.map(unitToAgentMapper)
        agentsBeforeCombat
                .forEach { fleeSim.addAgentA(it) }
        squad.enemies.map(unitToAgentMapper)
                .forEach { fleeSim.addAgentB(it) }
        ft("flee sim") { fleeSim.simulate(Config.fleeSimHorizon) }
        val lostUnits = agentsBeforeCombat.filter { it.health <= 0 }.map { it.userObject as SUnit }
        val eval = fleeSim.evalToInt(agentValueForPlayer)
        return SimResult(lostUnits, eval)
    }

    private fun simulateTicklers(ticklers: List<SUnit>, frames: Int): SimResult {
        simFS3.reset()
        val agentsBeforeCombat = ticklers.map(unitToAgentMapper)
        agentsBeforeCombat
                .forEach { simFS3.addAgentA(it) }
        squad.enemies
                .forEach { simFS3.addAgentB(unitToAgentMapper(it)) }
        ft("combat sim") { simFS3.simulate(frames) }
        val lostUnits = agentsBeforeCombat.filter { it.health <= 0 }.map { it.userObject as SUnit }
        val eval = sim.evalToInt(agentValueForPlayer)
        return SimResult(lostUnits, eval)
    }
}
