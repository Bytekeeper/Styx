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
import org.styx.micro.Attack
import org.styx.micro.KeepDistance
import org.styx.micro.Stop
import kotlin.math.max
import kotlin.math.min

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

    override fun exec() {
        diag.log(squad.toString())
        running()
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
        val bestEval = evaluator.optimizeEval(attackers.map { unitToAgentMapper(it) }, sim.agentsB)
        if (bestEval.agents.isNotEmpty() && bestEval.agents.size < attackers.size) {
            val toDismiss = attackers - bestEval.agents.map { it.userObject as SUnit }
            diag.log("Dismissing $toDismiss to improve eval from ${squad.fastEval} to ${bestEval.eval}")
            attackerLock.releaseItem(toDismiss)
        }
        val beforeCombat = sim.evalToInt(agentValueForPlayer)
        val valuesBeforeCombat = agentsBeforeCombat.map { it to agentValueForPlayer.applyAsInt(it) }.toMap()
        val sims = (1..1).map { simulateCombat(agentsBeforeCombat, Config.mediumSimHorizon) }
        val afterCombat = sims.last()
        val afterFlee = simulateFleeing()

        val scoreAfterCombat = afterCombat.eval.delta()
        val scoreAfterFleeing = afterFlee.eval.delta()
        val shouldFlee = scoreAfterFleeing > scoreAfterCombat + attackHysteresis + maxUseableWorkers * 3
                && !shouldSnipePylon(enemies)

        diag.log("COMBAT SCORE ${squad.name} after combat: $scoreAfterCombat; after fleeing: $scoreAfterFleeing; attackHysteresis $attackHysteresis")
        diag.log("SIM RESULTS ${squad.name} after combat: ${sim.agentsA} | ${sim.agentsB}; after flee ${fleeSim.agentsA} | ${fleeSim.agentsB}")

        var aggressiveness = 1.0
        if (workersAndBuildingsAreSave && shouldFlee) {
            diag.log("RETREAT ${squad.name} $shouldFlee")
            if (enemies.any { !it.flying }) {
                workersToUse = min(maxUseableWorkers, workersToUse + 1)
            }
            lastAttackHysteresis = 0

            val attackersToKeep = sim.agentsA.mapNotNull {
                val unit = it.userObject as SUnit
                if (it.attackCounter > 0 && unit.safe)
                    unit
                else
                    null
            }

            // Keep units that'd die anyway or are not in danger
            attackerLock.releaseItem(attackers - afterFlee.lostUnits - attackersToKeep)
            if (attackers.isEmpty())
                return
            else {
                diag.log("Will lose ${afterFlee.lostUnits} anyways, keep attacking. Will also not lose $attackers either way, so keep them going too")
            }
            aggressiveness = 0.5
        } else {
            diag.log("ATTACK ${squad.name}")
            squad.task = "Fight"

            if (scoreAfterCombat > scoreAfterFleeing)
                workersToUse = max(0, workersToUse - 1)
            else if (scoreAfterFleeing >= scoreAfterCombat && workersToUse < maxUseableWorkers && enemies.any { !it.flying }) {
                workersToUse = min(maxUseableWorkers, workersToUse + 1)
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
                    .forEach { simFS3.addAgentA(unitToAgentMapper(it)) }
            squad.enemies
                    .forEach { simFS3.addAgentB(unitToAgentMapper(it)) }
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
                is StayBack -> KeepDistance(move.unit)
                else -> error("Invalid move: $move")
            }

    private val unitToAgentMapper = { it: SUnit ->
        val agent = it.agent()
        if (it.enemyUnit && (it.gathering ||
                        it.unitType.isWorker && !it.visible) || (it.myUnit && !attackerLock.item.contains(it)) ||
                !it.hasPower)
            agent.setCooldown(Config.mediumSimHorizon)
        if (!it.unitType.canAttack() && it.unitType != UnitType.Terran_Bunker)
            agent.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        agent
    }

    private fun simulateCombat(agentsBeforeCombat: List<Agent>, frames: Int): SimResult {
        sim.simulate(frames)
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
        val agentsBeforeCombat = squad.mine.map {
            val agent = unitToAgentMapper(it)
            if (!it.flying)
                agent.setSpeedFactor(0.85f)
            agent
        }
        agentsBeforeCombat
                .forEach { fleeSim.addAgentA(it) }
        squad.enemies.map(unitToAgentMapper)
                .forEach { fleeSim.addAgentB(it) }
        fleeSim.simulate(Config.longSimHorizon)
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
        simFS3.simulate(frames)
        val lostUnits = agentsBeforeCombat.filter { it.health <= 0 }.map { it.userObject as SUnit }
        val eval = sim.evalToInt(agentValueForPlayer)
        return SimResult(lostUnits, eval)
    }
}
