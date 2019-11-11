package org.styx.squad

import org.bk.ass.sim.Agent
import org.bk.ass.sim.IntEvaluation
import org.styx.*
import org.styx.action.BasicActions
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
            (b.unitType.isBuilding || b.unitType.isWorker) && squad.enemies.any { it.inAttackRange(b, 64f) }
        }
        if (enemies.isEmpty() || retreatHysteresisFrames > 0 && workersAndBuildingsAreSave) {
            workersToUse = 0
            return NodeStatus.RUNNING
        }
        attackerLock.reacquire()
        val maxUseableWorkers = squad.myWorkers.size
        if (attackerLock.units.isEmpty() && maxUseableWorkers == 0) {
            workersToUse = 0
            return NodeStatus.RUNNING
        }
        val attackers = attackerLock.units

        if (!Styx.balance.direSituation) {
            val afterCombat = simulateCombat()
            val afterFlee = simulateFleeing()

            val scoreAfterCombat = afterCombat.eval.delta()
            val scoreAfterFleeing = afterFlee.eval.delta()
            val shouldFlee = scoreAfterFleeing > scoreAfterCombat + attackHysteresis + maxUseableWorkers * 3
            val scoreAfterWaitingForUpgrades = if (!shouldFlee && scoreAfterFleeing > scoreAfterCombat + maxUseableWorkers * 3)
                simulateWithUpgradesDone(7 * 24).eval.delta()
            else
                Int.MIN_VALUE

            val waitForUpgrade = scoreAfterWaitingForUpgrades > scoreAfterCombat + attackers.size * attackHysteresis / 7

            if (workersAndBuildingsAreSave && (waitForUpgrade || shouldFlee)) {
//                diag.log("Fight ${board.name} attack: $scoreAfterCombat flee: $scoreAfterFleeing upg: $scoreAfterWaitingForUpgrades ${board.all}")
                if (enemies.any { !it.flying }) {
                    workersToUse = min(maxUseableWorkers, workersToUse + 1)
                }
                lastAttackHysteresis = 0
                retreatHysteresisFrames = Config.retreatHysteresisFrames

                // Keep units that'd die anyway
                attackerLock.releaseUnits(attackers - afterFlee.lostUnits)
                if (attackers.isEmpty())
                    return NodeStatus.RUNNING
                else {
                    Styx.diag.log("Will lose $attackers anyways, keep attacking")
                }
            } else {
                squad.task = "Fight"

                lastAttackHysteresis = attackers.size * Config.attackerHysteresisValue
                if (scoreAfterCombat > scoreAfterFleeing + Config.attackerHysteresisValue)
                    workersToUse = max(0, workersToUse - 1)
                else if (scoreAfterFleeing > scoreAfterCombat && workersToUse < maxUseableWorkers && enemies.any { !it.flying }) {
                    workersToUse = min(maxUseableWorkers, workersToUse + 1)
                }
            }
        } else if (Styx.balance.direSituation) {
            workersToUse = maxUseableWorkers
        }

        val combatMoves = TargetEvaluator.bestCombatMoves(attackers, enemies)
        attackers.forEach { a ->
            when (val move = combatMoves[a]) {
                is AttackMove ->
                    if (move.enemy.visible)
                        BasicActions.attack(a, move.enemy)
                    else
                        BasicActions.move(a, move.enemy.position)
                is WaitMove ->
                    a.stop()
                // TODO fix releasing in locks
                else -> Styx.resources.releaseUnit(a)
            }
        }

        return NodeStatus.RUNNING
    }

    private val unitToAgentMapper = { it: SUnit ->
        val agent = it.agent()
        if (it.enemyUnit && it.gathering || (it.myUnit && !attackerLock.units.contains(it)))
            agent.setCooldown(Config.simHorizon)
        if (!it.unitType.canAttack())
            agent.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        agent
    }

    private fun simulateCombat(): SimResult {
        Styx.sim.reset()
        val agentsBeforeCombat = squad.mine.map(unitToAgentMapper)
        agentsBeforeCombat.forEach { Styx.sim.addAgentA(it) }
        squad.enemies.forEach {
            val a = it.agent()
            Styx.sim.addAgentB(a)
//            if (!it.unitType.canAttack())
//                a.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        }

        Styx.sim.simulate(Config.simHorizon)
        val lostUnits = agentsBeforeCombat.filter { it.health <= 0 }.map { it.userObject as SUnit }
        val eval = Styx.sim.evalToInt(agentValueForPlayer)
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
            if (!it.unitType.canAttack())
                a.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        }

        Styx.simFS3.simulate(Config.simHorizon)
        val lostUnits = agentsBeforeCombat.filter { it.health <= 0 }.map { it.userObject as SUnit }
        val eval = Styx.simFS3.evalToInt(agentValueForPlayer)
        return SimResult(lostUnits, eval)
    }

    private fun simulateFleeing(): SimResult {
        Styx.fleeSim.reset()
        val agentsBeforeCombat = squad.mine.map(unitToAgentMapper)
        agentsBeforeCombat
                .forEach { Styx.fleeSim.addAgentA(it) }
        squad.enemies.map(unitToAgentMapper)
                .forEach { Styx.fleeSim.addAgentB(it) }
        Styx.fleeSim.simulate(Config.simHorizon)
        val lostUnits = agentsBeforeCombat.filter { it.health <= 0 }.map { it.userObject as SUnit }
        val eval = Styx.fleeSim.evalToInt(agentValueForPlayer)
        return SimResult(lostUnits, eval)
    }
}
