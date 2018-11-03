package org.fttbot.task

import com.badlogic.gdx.math.Vector2
import org.bk.ass.Agent
import org.bk.ass.Simulator
import org.fttbot.Commands
import org.fttbot.FTTBot
import org.fttbot.estimation.CombatEval
import org.fttbot.info.*
import org.fttbot.task.move.AvoidCombat
import org.fttbot.task.move.JoinUp
import org.fttbot.task.move.KeepChokesClear
import org.fttbot.task.move.Kite
import org.fttbot.toPosition
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.*
import java.util.*
import kotlin.math.PI
import kotlin.math.min

class StaticAttacker(val attacker: Attacker, private val enemies: List<PlayerUnit>) : Action() {
    override fun processInternal(): TaskStatus {
        if (attacker is GroundAttacker && attacker.groundWeaponCooldown > 0 ||
                attacker is AirAttacker && attacker.airWeaponCooldown > 0) return TaskStatus.RUNNING
        val target = enemies.firstOrNull { attacker.canAttack(it) } ?: return TaskStatus.RUNNING
        Commands.attack(attacker, target)
        return TaskStatus.RUNNING
    }
}

class MobileAttacker(val attacker: MobileUnit, val combatData: CombatData) : Action() {
    private val rng = SplittableRandom()
    private var evasionPoint: Position? = null
    private val keepChokesClear by LazyTask { KeepChokesClear(attacker) }
    private val avoidCombat by LazyTask { AvoidCombat(attacker) }
    private val kite by LazyTask { Kite(attacker as Attacker, combatData) }
    private val joinUp by LazyTask { JoinUp(attacker) }

    override fun processInternal(): TaskStatus {
        val enemies = combatData.enemies
        val targetUnit = attacker.targetUnit

        attacker as Attacker

        if (combatData.enemies.isNotEmpty()) {
            if (combatData.engage ||
                    (combatData.simulationPerformance.first > 0.95 && combatData.simulationResult.first.none { it.userObject == attacker && it.health <= 0 })) {
                MyInfo.unitStatus[attacker] = "Attacking"
                if (!attacker.canMoveWithoutBreakingAttack)
                    return TaskStatus.RUNNING

                val candidates = enemies
                        .filter { attacker.hasWeaponAgainst(it) && it.isDetected }

                candidates.mapIndexed { index, candidate ->
                    candidate to attacker.getDistance(candidate) / (attacker.maxRange() + attacker.topSpeed * 10) + 0.2 * index +
                            (if (targetUnit == candidate) -2 else 0) +
                            (if (targetUnit is Larva || targetUnit is Egg) 2 else 0)
                }.minBy { it.second }?.first
                        ?.let { target ->
                            if (targetUnit != target && target.isVisible && !attacker.onCooldown) {
                                if (attacker.canAttack(target, 16)) {
                                    if (targetUnit != target) {
                                        Commands.attack(attacker, target)
                                        attacker.attack(target)
                                        return TaskStatus.RUNNING
                                    }
                                }
                            }
                            val goal = if (attacker.canAttack(target)) (attacker as MobileUnit).position else EnemyInfo.predictedPositionOf(target, attacker.getDistance(target) / attacker.topSpeed +
                                    FTTBot.remaining_latency_frames)

                            val potentialAttackers = attacker.potentialAttackers(32)
                            if (evasionPoint == null) {
                                evasionPoint = goal.add(Vector2(rng.nextInt(192).toFloat(), 0f).rotateRad((rng.nextDouble() * PI * 2).toFloat()).toPosition())
                            }
                            if (evasionPoint != null &&
                                    attacker.getDistance(evasionPoint!!) > 8 &&
                                    attacker.maxRangeVs(target) > target.getDistance(evasionPoint!!) &&
                                    potentialAttackers.count { it.maxRangeVs(attacker) < it.getDistance(evasionPoint) - 32 } < potentialAttackers.size) {
                                MyInfo.unitStatus[attacker] = "Evade"
                                if (evasionPoint!!.getDistance(attacker.targetPosition) > 8)
                                    Commands.move(attacker, evasionPoint!!)
                            } else if (goal.getDistance(attacker.targetPosition) > 48) {
                                evasionPoint = null
                                Commands.move(attacker, goal)
                            }
                            return TaskStatus.RUNNING
                        }
            }
        }

        return processInSequence(
                joinUp,
                kite,
                avoidCombat,
                keepChokesClear,
                Running())
    }

}

class CombatCluster(val cluster: Cluster<PlayerUnit>) : Task() {
    override val utility: Double = 1.0

    private val combatData = CombatData()

    private val attackerTasks = ItemToTaskMapper({
        cluster.myUnits
                .asSequence()
                .filterIsInstance<Attacker>()
                .filter { it !is Worker }
                .toList()
    }) {
        if (it is MobileUnit) MobileAttacker(it, combatData) else StaticAttacker(it, combatData.enemies)
    }

    override fun processInternal(): TaskStatus {
        val buddies = cluster.myUnits.map { FTTBot.agentFactory.of(it) }

        combatData.enemies.clear()
        val clusterIncludingEnemies = cluster.myUnits.first().myCluster
        clusterIncludingEnemies.enemySimUnits
                .filter { (it.userObject as PlayerUnit).getDistance(cluster.position) < 500 }
                .sortedByDescending { enemySim ->
                    val enemy = enemySim.userObject as PlayerUnit
                    val type = enemy.type
                    CombatEval.probabilityToWin(buddies, cluster.enemySimUnits - enemySim, 0.8f) +
                            ((enemy as? GroundAttacker)?.groundWeaponDamage ?: 0) / 100.0 +
                            ((enemy as? AirAttacker)?.airWeaponDamage ?: 0) / 100.0 +
                            (if (enemy is Worker) 0.01 else 0.0)
                }.forEach {
                    val enemy = it.userObject as PlayerUnit
                    if (enemy.isDetected)
                        combatData.enemies += enemy
                }


        val myRelevantUnits = cluster.myUnits.filter { it.isCombatRelevant() }
        val relevantEnemyUnits = clusterIncludingEnemies.enemyUnits.filter { it.isCombatRelevant() }
        simulator.resetUnits()
        myRelevantUnits.forEach { simulator.addAgentA(FTTBot.agentFactory.of(it)) }
        relevantEnemyUnits.forEach { simulator.addAgentB(FTTBot.agentFactory.of(it)) }
        simulator.simulate(6 * 24)

        combatData.simulationResult = simulator.agentsA to simulator.agentsB
        combatData.simulationPerformance = Cluster.combatPerformance(simulator.agentsA, myRelevantUnits) to Cluster.combatPerformance(simulator.agentsB, relevantEnemyUnits)
        combatData.enemies.addAll(clusterIncludingEnemies.enemyUnits.filter { !it.isCombatRelevant() && it.getDistance(cluster.position) < 500 && it !is Larva })
        combatData.cluster = cluster
        combatData.engage = combatData.simulationPerformance.first > min(0.99, combatData.simulationPerformance.second + if (combatData.engage) -0.02 else 0.05)
        return processParallel(attackerTasks().asSequence())
    }

    companion object {
        private val simulator = Simulator()
    }
}

class CombatData(val enemies: MutableList<PlayerUnit> = mutableListOf(), var simulationResult: Pair<Collection<Agent>, Collection<Agent>> = Pair(emptyList(), emptyList()),
                 var simulationPerformance: Pair<Double, Double> = Pair(0.0, 0.0),
                 var cluster: Cluster<PlayerUnit>? = null,
                 var engage: Boolean = false)


class CombatController : Task() {
    override val utility: Double = 1.0

    private val clusters = TPar(ItemToTaskMapper({ Cluster.squadClusters.clusters.map { it.userObject as Cluster<PlayerUnit> } }, {
        CombatCluster(it)
    }))

    override fun processInternal(): TaskStatus = clusters.process()

    companion object : TaskProvider {
        private val tasks = listOf(CombatController().nvr())
        override fun invoke(): List<Task> = tasks
    }
}