package org.styx.squad

import bwapi.Position
import org.bk.ass.collection.UnorderedCollection
import org.bk.ass.sim.Agent
import org.bk.ass.sim.AttackerBehavior
import org.bk.ass.sim.IntEvaluation
import org.styx.*
import org.styx.Styx.balance
import org.styx.Styx.bases
import org.styx.Styx.diag
import org.styx.Styx.evaluator
import org.styx.Styx.fleeSim
import org.styx.Styx.pendingUpgrades
import org.styx.Styx.resources
import org.styx.Styx.sim
import org.styx.Styx.simFS3
import org.styx.Styx.squads
import org.styx.Styx.units
import org.styx.action.BasicActions
import kotlin.math.max
import kotlin.math.min

class SquadBoard {
    var fastEval: Double = 0.5
    var mine = emptyList<SUnit>()
    var enemies = emptyList<SUnit>()
    var all = emptyList<SUnit>()
    var attackBias = 0
    val myCenter by LazyOnFrame {
        mine.fold(Position(0, 0)) { agg, p -> agg + p.position } / max(1, mine.size)
    }
    val center by LazyOnFrame {
        all.fold(Position(0, 0)) { agg, p -> agg + p.position } / max(1, all.size)
    }
    val myWorkers by lazy { mine.filter { it.unitType.isWorker } }

    override fun toString(): String = "[$fastEval, $mine, $enemies, $attackBias, $myCenter, $center]"
}

class SquadFightLocal(private val board: SquadBoard) : BTNode() {
    private var workersToUse = 0
    private val attackerLock = UnitLocks {
        resources.availableUnits
                .filter {
                    board.mine.contains(it) &&
                            !it.unitType.isWorker &&
                            !it.unitType.isBuilding &&
                            it.unitType.canAttack()
                }
    }
    private val workerAttackerLock = UnitLocks {
        resources.availableUnits.filter {
            board.mine.contains(it) &&
                    it.unitType.isWorker
        }.sortedByDescending { it.hitPoints * (if (it.gathering) 0.7 else 1.0) }
                .take(workersToUse)
    }

    override fun tick(): NodeStatus {
        val enemies = board.enemies
        val attackBias = board.attackBias
        board.attackBias = 0
        if (enemies.isEmpty()) {
            return NodeStatus.RUNNING
        }
        if (board.mine.none { it.unitType.isBuilding } && board.fastEval < 0.2)
            return NodeStatus.RUNNING
        attackerLock.reacquire()
        val maxUseableWorkers = board.myWorkers.size
        if (attackerLock.units.isEmpty() && maxUseableWorkers == 0)
            return NodeStatus.RUNNING
        workerAttackerLock.reacquire()
        val attackers = attackerLock.units + workerAttackerLock.units.take(workersToUse)

        if (!balance.direSituation) {
            val afterCombatEval = simulateCombat(enemies)
            val afterFleeEval = simulateFleeing(enemies)

            val scoreAfterCombat = afterCombatEval.delta()
            val scoreAfterFleeing = afterFleeEval.delta()
            val biasAgainstFleeing = attackers.size * 2
            val shouldFlee = scoreAfterFleeing > scoreAfterCombat + attackBias + biasAgainstFleeing + maxUseableWorkers * 3
            val scoreAfterWaitingForUpgrades = if (!shouldFlee && scoreAfterFleeing > scoreAfterCombat + maxUseableWorkers * 3)
                simulateWithUpgradesDone(enemies).delta()
            else
                Int.MIN_VALUE

            val waitForUpgrade = scoreAfterWaitingForUpgrades > scoreAfterCombat + biasAgainstFleeing + attackBias

            if (waitForUpgrade || shouldFlee) {
                if (attackBias != 0) {
                    diag.log("SquadFightLocal - RETREAT - $board - shouldFlee: $shouldFlee, " +
                            "waitForUpgrade: $waitForUpgrade, after-combat-score: $scoreAfterCombat, " +
                            "after-fleeing-score: $scoreAfterFleeing, after-upgrade-score: $scoreAfterWaitingForUpgrades, bias: $biasAgainstFleeing " +
                            ", attack-bias: $attackBias, workers-to-use: $workersToUse")
                }
                workersToUse = min(maxUseableWorkers, workersToUse + 1)
                board.attackBias = 0
                attackerLock.release()
                workerAttackerLock.release()
                return NodeStatus.RUNNING
            }
            if (attackBias == 0) {
                diag.log("SquadFightLocal - ATTACK - $board - shouldFlee: $shouldFlee, " +
                        "waitForUpgrade: $waitForUpgrade, after-combat-score: $scoreAfterCombat, " +
                        "after-fleeing-score: $scoreAfterFleeing, after-upgrade-score: $scoreAfterWaitingForUpgrades, bias: $biasAgainstFleeing, " +
                        ", attack-bias: $attackBias, workers-to-use: $workersToUse")
            }
            board.attackBias = attackers.size * 4
            if (scoreAfterCombat > scoreAfterFleeing + 16)
                workersToUse = max(0, workersToUse - 1)
            else if (scoreAfterFleeing > scoreAfterCombat + biasAgainstFleeing && workersToUse < maxUseableWorkers) {
                workersToUse = min(maxUseableWorkers, workersToUse + 1)
            }
        } else {
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
                 else -> resources.releaseUnit(a)
            }
        }

        return NodeStatus.RUNNING
    }

    private val unitToAgentMapper = { it: SUnit ->
        val agent = it.agent()
        if (it.enemyUnit && it.gathering || (it.myUnit && !workerAttackerLock.units.contains(it) && !attackerLock.units.contains(it)))
            agent.setCooldown(128)
        if (!it.unitType.canAttack())
            agent.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        agent
    }

    private fun simulateCombat(enemies: List<SUnit>): IntEvaluation {
        sim.reset()
        board.mine.map(unitToAgentMapper)
                .forEach { sim.addAgentA(it) }
        board.enemies.forEach {
            val a = it.agent()
            sim.addAgentB(a)
            if (!it.unitType.canAttack())
                a.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        }

        sim.simulate(128)
        return sim.evalToInt(agentValueForPlayer)
    }

    private fun simulateWithUpgradesDone(enemies: List<SUnit>): IntEvaluation {
        simFS3.reset()
        board.mine.map(unitToAgentMapper).forEach {
            simFS3.addAgentA(pendingUpgrades.agentWithPendingUpgradesApplied(it))
                }
        enemies.forEach {
            val a = pendingUpgrades.agentWithPendingUpgradesApplied(it.agent())
            simFS3.addAgentB(a)
            if (!it.unitType.canAttack())
                a.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        }

        simFS3.simulate(128)
        return simFS3.evalToInt(agentValueForPlayer)
    }

    private fun simulateFleeing(enemies: List<SUnit>): IntEvaluation {
        fleeSim.reset()
        board.mine.map(unitToAgentMapper)
                .forEach { fleeSim.addAgentA(it) }
        enemies.map(unitToAgentMapper)
                .forEach { fleeSim.addAgentB(it) }
        fleeSim.simulate(128)
        return fleeSim.evalToInt(agentValueForPlayer)
    }

    override fun reset() {
        workersToUse = 0
    }
}

class NoAttackIfGatheringBehavior : AttackerBehavior() {
    override fun simUnit(frameSkip: Int, agent: Agent, allies: UnorderedCollection<Agent>, enemies: UnorderedCollection<Agent>): Boolean =
            (agent.userObject as? SUnit)?.gathering != true && super.simUnit(frameSkip, agent, allies, enemies)
}

class SquadAttack(private val board: SquadBoard) : BTNode() {
    private val attackerLock = UnitLocks { resources.availableUnits.filter { board.mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun tick(): NodeStatus {
        val enemySquads = squads.squads.filter { it.enemies.isNotEmpty() }
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty()) return NodeStatus.RUNNING
        val attackers = attackerLock.units
        val candidates = enemySquads.map {
            val myAgents = (attackers + it.mine).distinct().filter { it.unitType.canAttack() && it.unitType.canMove() }.map { it.agent() }
            val enemies = (board.enemies + it.enemies).distinct().map { it.agent() }
            it to evaluator.evaluate(myAgents, enemies)
        }
        val targetSquad = bestSquadToSupport(candidates)
                ?: desperateAttemptToWinSquad(candidates)
        if (targetSquad != null) {
            val targetPosition = targetSquad.mine.filter { !it.flying }.minBy { it.distanceTo(targetSquad.center) }?.position
                    ?: targetSquad.enemies.filter { !it.flying }.minBy { it.distanceTo(targetSquad.center) }?.position
            attackers.forEach { attacker ->
                if (targetPosition != null) {
//                    val force = Potential.groundAttract(attacker, targetPosition) +
//                            Potential.keepGroundCompany(attacker, 32) * 0.2
//                    Potential.apply(attacker, force)
                    BasicActions.move(attacker, targetPosition)
                }
            }
        } else {
            attackerLock.release()
        }
        return NodeStatus.RUNNING
    }

    private fun desperateAttemptToWinSquad(candidates: List<Pair<SquadBoard, Double>>) =
            if (balance.direSituation)
                candidates.maxBy { it.second }?.let { if (it.second > 0.3) it.first else null }
            else
                null

    private fun bestSquadToSupport(candidates: List<Pair<SquadBoard, Double>>) =
            candidates.filter { it.second > 0.55 }.minBy { it.second }?.first
}

class SquadBackOff(private val board: SquadBoard) : BTNode() {
    private val attackerLock = UnitLocks {
        resources.availableUnits.filter {
            board.mine.contains(it) &&
                    !it.unitType.isWorker &&
                    it.unitType.canMove() &&
                    it.unitType.canAttack()
        }
    }

    override fun tick(): NodeStatus {
        if (board.fastEval >= 0.5)
            return NodeStatus.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty())
            return NodeStatus.RUNNING
        val targetSquad = squads.squads
                .filterNot { it == board }
                .minBy { it.enemies.size * 2 - it.mine.count { it.unitType.canAttack() } }
                ?: return NodeStatus.RUNNING
        if (targetSquad.mine.isEmpty()) {
            attackerLock.units.forEach { attacker ->
                val target = TargetEvaluator.bestTarget(attacker, units.enemy) ?: return@forEach
                BasicActions.attack(attacker, target)
            }
            return NodeStatus.RUNNING
        }
        attackerLock.units.forEach { a ->
            if (a.distanceTo(targetSquad.myCenter) > 96) {
                BasicActions.move(a, targetSquad.myCenter)
//                val force = Potential.groundAttract(a, targetSquad.myCenter)
//                Potential.apply(a, force)
            } else if (a.moving) {
                a.stop()
            }
        }
        return NodeStatus.RUNNING
    }
}

class SquadScout(private val board: SquadBoard) : BTNode() {
    private val attackerLock = UnitLocks {
        resources.availableUnits.filter {
            board.mine.contains(it) &&
                    !it.unitType.isWorker &&
                    it.unitType.canMove() &&
                    it.unitType.canAttack()
        }
    }

    override fun tick(): NodeStatus {
        if (bases.potentialEnemyBases.isEmpty())
            return NodeStatus.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty())
            return NodeStatus.RUNNING
        val targetBase = bases.potentialEnemyBases.minBy { it.center.getApproxDistance(board.myCenter) }!!
        attackerLock.units.forEach {
            BasicActions.move(it, targetBase.center)
        }
        return NodeStatus.RUNNING
    }
}