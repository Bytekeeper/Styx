package org.styx.squad

import bwapi.Position
import bwapi.UnitType
import org.bk.ass.collection.UnorderedCollection
import org.bk.ass.sim.Agent
import org.bk.ass.sim.AttackerBehavior
import org.bk.ass.sim.Evaluator
import org.bk.ass.sim.Simulator
import org.styx.*
import org.styx.Styx.fleeSim
import org.styx.Styx.resources
import org.styx.Styx.sim
import org.styx.Styx.squads
import org.styx.Styx.units
import org.styx.action.BasicActions
import java.util.function.ToIntFunction
import kotlin.math.max

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
        all.fold(Position(0, 0)) { agg, p -> agg + p.position } / max(1, mine.size)
    }
}

class SquadFightLocal(private val board: SquadBoard) : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { board.mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }
    private val ATTACK_POWER = ToIntFunction<Agent> { agent ->
        val unitType = (agent.userObject as SUnit).unitType
        val airDPF = ((unitType.airWeapon().damageAmount() * unitType.maxAirHits()) / unitType.airWeapon().damageCooldown().toDouble()).orZero()
        val groundDPF = ((unitType.groundWeapon().damageAmount() * unitType.maxGroundHits()) / unitType.groundWeapon().damageCooldown().toDouble()).orZero()
        (max(airDPF, groundDPF) * 3 +
                Simulator.HEALTH_AND_HALFED_SHIELD.applyAsInt(agent) / 3).toInt()
    }

    override fun tick(): NodeStatus {
        val enemies = board.enemies
        if (enemies.isEmpty()) return NodeStatus.RUNNING
        if (board.mine.none { it.unitType.isBuilding } && board.fastEval < 0.2)
            return NodeStatus.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty()) return NodeStatus.RUNNING
        val attackers = attackerLock.units

        sim.reset()
        attackers.filter { !it.gathering && it.completed }.forEach { sim.addAgentA(it.agent()) }
        enemies.forEach {
            val a = it.agent()
            sim.addAgentB(a)
            if (!it.unitType.canAttack())
                a.setAttackTargetPriority(Agent.TargetingPriority.MEDIUM)
        }

        sim.simulate(192)
        val hsAfter = sim.evalToInt(ATTACK_POWER)

        fleeSim.reset()
        attackers.filter { !it.gathering && it.completed }.forEach { fleeSim.addAgentA(it.agent()) }
        enemies.map { it.agent() }.forEach { fleeSim.addAgentB(it) }
        fleeSim.simulate(192)
        val hsAfterFlee = fleeSim.evalToInt(ATTACK_POWER)

        val scoreAfterCombat = hsAfter.evalA - hsAfter.evalB
        val scoreAfterFleeing = hsAfterFlee.evalA - hsAfterFlee.evalB
        if (scoreAfterFleeing > scoreAfterCombat + board.attackBias + attackers.size * 2) {
            board.attackBias = 0
            attackerLock.release()
            return NodeStatus.RUNNING
        }
        board.attackBias = attackers.size * 3

        val combatMoves = TargetEvaluator.bestCombatMoves(attackers, enemies)
        attackers.forEach { a ->
            when (val move = combatMoves[a]) {
                is AttackMove ->
                    if (move.enemy.visible) BasicActions.attack(a, move.enemy) else BasicActions.move(a, move.enemy.position)
                 else -> resources.releaseUnit(a)
            }
        }

        return NodeStatus.RUNNING
    }
}

class NoAttackIfGatheringBehavior : AttackerBehavior() {
    override fun simUnit(frameSkip: Int, agent: Agent, allies: UnorderedCollection<Agent>, enemies: UnorderedCollection<Agent>): Boolean =
            (agent.userObject as? SUnit)?.gathering != true && super.simUnit(frameSkip, agent, allies, enemies)
}

class SquadAttack(private val board: SquadBoard) : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { board.mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    private val evaluator = Evaluator()

    override fun tick(): NodeStatus {
        val enemySquads = squads.squads.filter { it.enemies.isNotEmpty() }
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty()) return NodeStatus.RUNNING
        val attackers = attackerLock.units
        val targetSquad = enemySquads.map {
            val myAgents = (attackers + it.mine).distinct().filter { it.unitType.canAttack() && it.unitType.canMove() }.map { it.agent() }
            val enemies = (board.enemies + it.enemies).distinct().map { it.agent() }
            it to evaluator.evaluate(myAgents, enemies)
        }.filter { it.second > 0.6 }.minBy { it.second }?.first
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
}

class SquadBackOff(private val board: SquadBoard) : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { board.mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun tick(): NodeStatus {
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty()) return NodeStatus.RUNNING
        val targetSquad = squads.squads
                .filterNot { it == board }
                .minBy { it.enemies.size * 2 - it.mine.count { it.unitType.canAttack() } } ?: return NodeStatus.RUNNING
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