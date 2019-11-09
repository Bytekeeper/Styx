package org.styx.squad

import bwapi.Position
import org.bk.ass.collection.UnorderedCollection
import org.bk.ass.sim.Agent
import org.bk.ass.sim.AttackerBehavior
import org.bk.ass.sim.IntEvaluation
import org.styx.*
import org.styx.Config.attackerHysteresisValue
import org.styx.Config.retreatHysteresisFrames
import org.styx.Config.simHorizon
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

val adjectives = listOf("stinging", "red", "running", "weak", "blazing", "awful", "spiteful", "loving", "hesitant", "raving", "hunting")
val nouns = listOf("squirrel", "bee", "scorpion", "coyote", "rabbit", "marine-eater", "void", "space-cowboy", "wallaby")

class SquadBoard {
    val name = "${adjectives.random()} ${nouns.random()}"
    var task = ""
    var fastEval: Double = 0.5
    var mine = emptyList<SUnit>()
    var enemies = emptyList<SUnit>()
    var all = emptyList<SUnit>()
    var attackHysteresis = 0
    var retreatHysteresisFrames = 0
    val myCenter by LazyOnFrame {
        mine.fold(Position(0, 0)) { agg, p -> agg + p.position } / max(1, mine.size)
    }
    val center by LazyOnFrame {
        all.fold(Position(0, 0)) { agg, p -> agg + p.position } / max(1, all.size)
    }
    val myWorkers by lazy { mine.filter { it.unitType.isWorker } }

    override fun toString(): String = "$name: [$fastEval, $mine, $enemies, $attackHysteresis, $myCenter, $center]"
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
        }.sortedByDescending { it.hitPoints * (if (it.gathering || it.carrying) 0.7 else 1.0) }
                .take(workersToUse)
    }

    override fun tick(): NodeStatus {
        val enemies = board.enemies
        val attackHysteresis = board.attackHysteresis
        board.attackHysteresis = 0
        board.retreatHysteresisFrames--
        val noneOfMyBuildingsAreInDanger = board.mine.none { b ->
            b.unitType.isBuilding && board.enemies.any { it.inAttackRange(b, 64f) }
        }
        if (enemies.isEmpty() || board.retreatHysteresisFrames > 0 && noneOfMyBuildingsAreInDanger) {
            return NodeStatus.RUNNING
        }
        attackerLock.reacquire()
        val maxUseableWorkers = board.myWorkers.size
        if (attackerLock.units.isEmpty() && maxUseableWorkers == 0)
            return NodeStatus.RUNNING
        workerAttackerLock.reacquire()
        val attackers = attackerLock.units + workerAttackerLock.units.take(workersToUse)


        if (!balance.direSituation) {
            val afterCombatEval = simulateCombat()
            val afterFleeEval = simulateFleeing()

            val scoreAfterCombat = afterCombatEval.delta()
            val scoreAfterFleeing = afterFleeEval.delta()
            val shouldFlee = scoreAfterFleeing > scoreAfterCombat + attackHysteresis + maxUseableWorkers * 3
            val scoreAfterWaitingForUpgrades = if (!shouldFlee && scoreAfterFleeing > scoreAfterCombat + maxUseableWorkers * 3)
                simulateWithUpgradesDone(7 * 24).delta()
            else
                Int.MIN_VALUE

            val waitForUpgrade = scoreAfterWaitingForUpgrades > scoreAfterCombat + attackers.size * attackHysteresis / 7

            if (noneOfMyBuildingsAreInDanger && (waitForUpgrade || shouldFlee)) {
                if (attackHysteresis != 0 && Config.logEnabled) {
                    val values = ObjectKeyMapAttachment("std::string",
                            "std::string",
                            listOf("shouldFlee" to shouldFlee,
                                    "waitForUpgrade" to waitForUpgrade,
                                    "scoreAfterCombat" to scoreAfterCombat,
                                    "scoreAfterFleeing" to scoreAfterFleeing,
                                    "scoreAfterWaitingForUpgrades" to scoreAfterWaitingForUpgrades,
                                    "attackHysteresis" to attackHysteresis,
                                    "workersToUse" to workersToUse)
                                    .map { listOf(it.first, it.second.toString()) })
                    diag.log("SquadFightLocal - RETREAT - ${board.center.toWalkPosition().diag()}"
                            , values,
                            ObjectKeyMapAttachment("Unit*", "Unit*",
                                    board.mine.map {
                                        val dunit = DUnit(it.id)
                                        listOf(dunit, dunit)
                                    }))
                }
                workersToUse = min(maxUseableWorkers, workersToUse + 1)
                board.attackHysteresis = 0
                board.retreatHysteresisFrames = retreatHysteresisFrames
                attackerLock.release()
                workerAttackerLock.release()
                return NodeStatus.RUNNING
            }
            if (attackHysteresis == 0  && Config.logEnabled) {
                val values = ObjectKeyMapAttachment("std::string",
                        "std::string",
                        listOf("shouldFlee" to shouldFlee,
                                "waitForUpgrade" to waitForUpgrade,
                                "scoreAfterCombat" to scoreAfterCombat,
                                "scoreAfterFleeing" to scoreAfterFleeing,
                                "scoreAfterWaitingForUpgrades" to scoreAfterWaitingForUpgrades,
                                "attackHysteresis" to attackHysteresis,
                                "workersToUse" to workersToUse)
                                .map { listOf(it.first, it.second.toString()) })
                diag.log("SquadFightLocal - ATTACK - ${board.center.toWalkPosition().diag()}"
                        , values,
                        ObjectKeyMapAttachment("Unit*", "Unit*",
                                board.mine.map {
                                    val dunit = DUnit(it.id)
                                    listOf(dunit, dunit)
                                }))
            }
            board.attackHysteresis = attackers.size * attackerHysteresisValue
            if (scoreAfterCombat > scoreAfterFleeing + attackerHysteresisValue / 2)
                workersToUse = max(0, workersToUse - 1)
            else if (scoreAfterFleeing > scoreAfterCombat && workersToUse < maxUseableWorkers) {
                workersToUse = min(maxUseableWorkers, workersToUse + 1)
            }
        } else if (balance.direSituation) {
            workersToUse = maxUseableWorkers
        }
        board.task = "Fight"

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
            agent.setCooldown(simHorizon)
        if (!it.unitType.canAttack())
            agent.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        agent
    }

    private fun simulateCombat(): IntEvaluation {
        sim.reset()
        board.mine.map(unitToAgentMapper)
                .forEach { sim.addAgentA(it) }
        board.enemies.forEach {
            val a = it.agent()
            sim.addAgentB(a)
//            if (!it.unitType.canAttack())
//                a.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        }

        sim.simulate(simHorizon)
        return sim.evalToInt(agentValueForPlayer)
    }

    private fun simulateWithUpgradesDone(frames: Int): IntEvaluation {
        simFS3.reset()
        board.mine.map(unitToAgentMapper).forEach {
            simFS3.addAgentA(pendingUpgrades.agentWithPendingUpgradesApplied(it, frames))
        }
        board.enemies.forEach {
            val a = pendingUpgrades.agentWithPendingUpgradesApplied(it.agent(), frames)
            simFS3.addAgentB(a)
            if (!it.unitType.canAttack())
                a.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        }

        simFS3.simulate(simHorizon)
        return simFS3.evalToInt(agentValueForPlayer)
    }

    private fun simulateFleeing(): IntEvaluation {
        fleeSim.reset()
        board.mine.map(unitToAgentMapper)
                .forEach { fleeSim.addAgentA(it) }
        board.enemies.map(unitToAgentMapper)
                .forEach { fleeSim.addAgentB(it) }
        fleeSim.simulate(simHorizon)
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
        // 0.5 - No damage to either side; != 0.5 - Flee due to combat sim
        if (board.fastEval != 0.5)
            return NodeStatus.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty())
            return NodeStatus.RUNNING
        val attackers = attackerLock.units
        val enemySquads = squads.squads.filter { it.enemies.isNotEmpty() }
        val candidates = enemySquads.map {
            val myAgents = (attackers + it.mine).distinct().filter { it.unitType.canAttack() && it.unitType.canMove() }.map { it.agent() }
            val enemies = (board.enemies + it.enemies).distinct().map { it.agent() }
            it to evaluator.evaluate(myAgents, enemies)
        }
        val targetSquad = bestSquadToSupport(candidates)
                ?: desperateAttemptToWinSquad(candidates)
        if (targetSquad != null) {
            board.task = "Attack"
            val targetPosition = targetSquad.mine.filter { !it.flying }.minBy { it.distanceTo(targetSquad.myCenter) }?.position
                    ?: targetSquad.enemies.filter { !it.flying }.minBy { it.distanceTo(targetSquad.center) }?.position
            attackers.forEach { attacker ->
                if (targetPosition != null && attacker.threats.isEmpty()) {
//                    val force = Potential.groundAttract(attacker, targetPosition) +
//                            Potential.keepGroundCompany(attacker, 32) * 0.2
//                    Potential.apply(attacker, force)
                    BasicActions.move(attacker, targetPosition)
                } else {
                    resources.releaseUnit(attacker)
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
            candidates.filter { it.first != board && it.second > 0.6 }.maxBy { it.second }?.first
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
        // 0.5 - no need to run away
        if (board.fastEval == 0.5) {
            return NodeStatus.RUNNING
        }
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty())
            return NodeStatus.RUNNING
        val targetSquad = squads.squads
                .maxBy { it.fastEval - it.enemies.size / 7.0 + it.mine.size / 7.0 + it.center.getDistance(board.center) / 2500.0 }
                ?: return NodeStatus.RUNNING
        if (targetSquad.mine.isEmpty()) {
            attackerLock.units.forEach { attacker ->
                val target = TargetEvaluator.bestTarget(attacker, units.enemy) ?: return@forEach
                BasicActions.attack(attacker, target)
            }
            return NodeStatus.RUNNING
        }
        board.task = "Back Off"
        attackerLock.units.forEach { a ->
            if (board.enemies.any { it.inAttackRange(a, 192f) } &&
                    a.distanceTo(targetSquad.myCenter) > 32) {
                BasicActions.move(a, targetSquad.myCenter)
//                val force = Potential.groundAttract(a, targetSquad.myCenter)
//                Potential.apply(a, force)
            } else if (a.moving && a.distanceTo(board.myCenter) < 96) {
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
        board.task = "Scout"
        attackerLock.units.forEach {
            BasicActions.move(it, targetBase.center)
        }
        return NodeStatus.RUNNING
    }
}