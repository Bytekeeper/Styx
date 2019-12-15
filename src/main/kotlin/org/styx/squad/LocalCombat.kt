package org.styx.squad

import bwapi.UnitType
import org.bk.ass.sim.Agent
import org.bk.ass.sim.IntEvaluation
import org.styx.*
import org.styx.Styx.diag
import org.styx.Styx.fleeSim
import org.styx.Styx.ft
import org.styx.Styx.sim
import org.styx.action.BasicActions
import org.styx.micro.Combat
import org.styx.micro.Potential
import kotlin.math.max
import kotlin.math.min

data class SimResult(val lostUnits: List<SUnit>, val eval: IntEvaluation)

class LocalCombat(private val squad: Squad) : BTNode() {
    private var workersToUse = 0
    private var lastAttackHysteresis = 0
    private var retreatHysteresisFrames = 0

    private val attackerLock = UnitLocks {
        val candidates = Styx.resources.availableUnits
                .filter { squad.mine.contains(it) }
        val combatUnits = candidates.filter {
            !it.unitType.isWorker &&
                    !it.unitType.isBuilding &&
                    it.unitType.canAttack()
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
        retreatHysteresisFrames--
        val workersAndBuildingsAreSave = squad.mine.none { b ->
            (b.unitType.isBuilding || b.unitType.isWorker) && squad.enemies.any { it.inAttackRange(b, 64) }
        } || squad.mine.none { it.unitType.isBuilding }
        if (workersAndBuildingsAreSave) {
            workersToUse = 0
        }
        if (enemies.isEmpty() || retreatHysteresisFrames > 0 && workersAndBuildingsAreSave) {
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
        val valuesBeforeCombat = agentsBeforeCombat.map { it to agentValueForPlayer.applyAsInt(it) }.toMap()
        val sims = (1..1).map { simulateCombat(agentsBeforeCombat, Config.simHorizon) }
        val afterCombat = sims.last()
        val afterFlee = simulateFleeing()

        val scoreAfterCombat = afterCombat.eval.delta()
        val scoreAfterFleeing = afterFlee.eval.delta()
        val shouldFlee = scoreAfterFleeing > scoreAfterCombat + attackHysteresis + maxUseableWorkers * 3

//            val scoreAfterWaitingForUpgrades = if (!shouldFlee && scoreAfterFleeing > scoreAfterCombat + maxUseableWorkers * 3)
//                simulateWithUpgradesDone(5 * 24).eval.delta()
//            else
//                Int.MIN_VALUE
//
//            val waitForUpgrade = false && scoreAfterWaitingForUpgrades > scoreAfterCombat + attackers.size * Config.attackerHysteresisValue / 2

        if (workersAndBuildingsAreSave && shouldFlee) {
            diag.log("Squad retreat ${squad.name} $shouldFlee - ${sim.agentsA} | ${sim.agentsB} - when fleeing ${fleeSim.agentsA} | ${fleeSim.agentsB}")
            if (enemies.any { !it.flying }) {
                workersToUse = min(maxUseableWorkers, workersToUse + 1)
            }
            lastAttackHysteresis = 0
            retreatHysteresisFrames = Config.retreatHysteresisFrames

            val valuesAfterCombat = sim.agentsA.map { it to agentValueForPlayer.applyAsInt(it) }
            val attackersToKeep = valuesAfterCombat.filter { (a, v) ->
                a.attackCounter > 0 && v * 4.0 > (valuesBeforeCombat[a] ?: error("More units after combat? Not really..."))
            }.map { it.first.userObject as SUnit }

            // Keep units that'd die anyway
            attackerLock.releaseUnits(attackers - afterFlee.lostUnits - attackersToKeep)
            if (attackers.isEmpty())
                return NodeStatus.RUNNING
            else {
                diag.log("Will lose ${afterFlee.lostUnits} anyways, keep attacking. Will also not lose $attackersToKeep either way, so keep them going too")
            }
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
            performAttack(attackers, enemies)
        }

        return NodeStatus.RUNNING
    }

    private fun performAttack(attackers: List<SUnit>, enemies: List<SUnit>) {
        val combatMoves = ft("best combat moves") { TargetEvaluator.bestCombatMoves(attackers, enemies) }
        diag.log("Squad combat moves ${squad.name} $combatMoves")
        attackers.forEach { a ->
            when (val move = combatMoves[a]) {
                is AttackMove -> Combat.attack(a, move.enemy)
                is WaitMove ->
                    a.stop()
                // TODO fix releasing in locks - don't use lock.release! It will crash with ConcurrentModification
                else -> Styx.resources.releaseUnit(a)
            }
        }
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

    private fun simulateWithUpgradesDone(frames: Int): SimResult {
        Styx.simFS3.reset()
        val agentsBeforeCombat = squad.mine.map(unitToAgentMapper)
        agentsBeforeCombat.forEach {
            Styx.simFS3.addAgentA(Styx.pendingUpgrades.agentWithPendingUpgradesApplied(it, frames))
        }
        squad.enemies.forEach {
            val a = Styx.pendingUpgrades.agentWithPendingUpgradesApplied(it.agent(), frames)
            Styx.simFS3.addAgentB(a)
        }

        ft("with upgrade sim") { Styx.simFS3.simulate(Config.simHorizon) }
        val lostUnits = agentsBeforeCombat.filter { it.health <= 0 }.map { it.userObject as SUnit }
        val eval = Styx.simFS3.evalToInt(agentValueForPlayer)
        return SimResult(lostUnits, eval)
    }
}
