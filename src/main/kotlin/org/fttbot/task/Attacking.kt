package org.fttbot.task

import com.badlogic.gdx.math.Vector2
import org.fttbot.*
import org.fttbot.estimation.CombatEval
import org.fttbot.info.*
import org.locationtech.jts.algorithm.distance.DistanceToPoint
import org.locationtech.jts.algorithm.distance.PointPairDistance
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import java.util.*
import kotlin.math.PI

class ManageAttacker(val attacker: Attacker, val _enemies: List<PlayerUnit>? = null) : Task() {
    private val rng = SplittableRandom()
    private var evasionPoint: Position? = null

    override val utility: Double
        get() = 1.0

    override fun processInternal(): TaskStatus {
        val myCluster = attacker.myCluster
        val enemies = _enemies ?: myCluster.enemyUnits
        val id = attacker.id
        val isFlying = attacker.isFlying
        val position = attacker.position
        val targetUnit = attacker.targetUnit

        if (attacker is MobileUnit) {
            if (myCluster.enemyUnits.isNotEmpty()) {
                if (myCluster.attackEval.second >= 0.6 && myCluster.attackEval.first.any { (it.userObject as PlayerUnit).id == id }) {

                    // If we would die in the next 96 frames, don't attack (stupid logic :) )
                    val sim = myCluster.attackSim
                    if (sim.first.any { it.userObject == attacker }) {
                        MyInfo.unitStatus[attacker] = "Attacking"
                        if (!attacker.canMoveWithoutBreakingAttack)
                            return TaskStatus.RUNNING

                        val candidates = enemies
                                .filter { attacker.hasWeaponAgainst(it) && it.isDetected }

                        val target = candidates.mapIndexed { index, candidate ->
                            candidate to attacker.getDistance(candidate) + 8000 * index / (attacker.maxRange() + attacker.topSpeed * 10) -
                                    (if (targetUnit == candidate) -60 else 0)
                        }.minBy { it.second }?.first ?: return TaskStatus.RUNNING
                        if (targetUnit != target && target.isVisible) {
                            if (attacker.canAttack(target, 48)) {
                                return TaskStatus.RUNNING
                            }
                        }
                        val goal = EnemyInfo.predictedPositionOf(target, attacker.getDistance(target) / attacker.topSpeed)

                        val potentialAttackers = attacker.potentialAttackers(32)
                        if (evasionPoint == null || potentialAttackers.count { it.maxRangeVs(attacker) < it.getDistance(evasionPoint) - 32 } >= potentialAttackers.size) {
                            evasionPoint = goal.add(Vector2(rng.nextInt(192).toFloat(), 0f).rotateRad((rng.nextDouble() * PI * 2).toFloat()).toPosition())
                        }
                        if (evasionPoint != null &&
                                attacker.getDistance(evasionPoint!!) > 8 &&
                                attacker.maxRangeVs(target) > target.getDistance(evasionPoint!!) &&
                                potentialAttackers.count { it.maxRangeVs(attacker) < it.getDistance(evasionPoint) - 32 } < potentialAttackers.size) {
                            MyInfo.unitStatus[attacker] = "Evade"
                            if (evasionPoint!!.getDistance(attacker.targetPosition) < 8)
                                attacker.move(evasionPoint)
                        } else if (goal.getDistance(attacker.targetPosition) > 48) {
                            evasionPoint = null
                            attacker.move(goal)
                        }
                        return TaskStatus.RUNNING
                    }
                }
            } else {
                val candidates = Cluster.clusters
                        .asSequence()
                        .filter { it.enemyUnits.isNotEmpty() && it != myCluster }
                        .mapNotNull {
                            val enemies = it.enemySimUnits.filter { (it.userObject as PlayerUnit).isCombatRelevant() }
                            if (enemies.isEmpty() && it.enemyUnits.any { attacker.hasWeaponAgainst(it) })
                                it to 1.0
                            else {
                                val eval = CombatEval.probabilityToWin((myCluster.mySimUnits + it.mySimUnits).filter { (it.userObject as PlayerUnit).isCombatRelevant() && it.userObject !is Worker },
                                        enemies)
                                if (eval < 0.6 && it.myUnits.none { u -> u is Building }) null else it to eval
                            }
                        }
                candidates.filter { c ->
                    val cDistance = c.first.position.getDistance(position)
                    val cDir = c.first.position.minus(position).toVector().nor()
                    candidates.none { o ->
                        val otherPosition = o.first.position
                        otherPosition.getDistance(position) < cDistance && o.second < c.second &&
                                otherPosition.minus(position).toVector().nor().dot(cDir) > 0.7f
                    }
                }.minBy { it.second * it.first.position.getDistance(position) }?.let { (target, _) ->
                    MyInfo.unitStatus[attacker] = "Seeking Cluster in need"
                    val targetPosition = target.enemyUnits.minBy { it.getDistance(attacker) }!!.position
                    if (attacker.targetPosition.getDistance(targetPosition) > 192) {
                        attacker.move(targetPosition)
                    }
                    return TaskStatus.RUNNING
                }

            }

            val force = Vector2()
            val threats = attacker.potentialAttackers(96)
            if (threats.isEmpty() && attacker.maxRange() > 64) {
                val enemies = UnitQuery.enemyUnits.inRadius(attacker, 400) + EnemyInfo.seenUnits.filter { it.getDistance(attacker) <= 400 }
                enemies.asSequence()
                        .filter {
                            attacker.hasWeaponAgainst(it) && it.isDetected &&
                                    (myCluster.attackEval.second >= 0.5 || it !is Attacker || it.maxRangeVs(attacker) < attacker.maxRangeVs(it))
                        }.filter { target ->
                            val pos = position.minus(target.position).toVector().setLength(attacker.maxRangeVs(target).toFloat()).toPosition().plus(target.position)
                            enemies.none { e -> val wpn = e.getWeaponAgainst(attacker)
                                wpn.type().damageAmount() > 0 && wpn.maxRange() >= e.getDistance(pos) }
                        }.minBy { it.getDistance(attacker) }
                        ?.let {
                            MyInfo.unitStatus[attacker] = "Kiting"
                            if (targetUnit != it && it.isDetected) {
                                attacker.attack(it)
                            } else if (attacker.targetPosition.getDistance(it.position) > 64) {
                                attacker.move(it.position)
                            }
                            return TaskStatus.RUNNING
                        }
                val myAttackers = myCluster.units.count { it is Attacker && it is MobileUnit }
                val bestCluster = Cluster.clusters
                        .asSequence()
                        .filter { it.units.count { it is Attacker && it is MobileUnit } > myAttackers }
                        .minBy { it.position.getDistance(position) } ?: myCluster
                if (attacker.getDistance(bestCluster.position) > 200) {
                    Potential.joinAttraction(force, attacker, bestCluster.myUnits)
                }
            } else {
                if (isFlying) {
                    Potential.addSafeAreaAttractionDirect(force, attacker)
                    Potential.addWallAttraction(force, attacker)
                } else {
                    Potential.addSafeAreaAttraction(force, attacker)
                }
                if (!isFlying) {
                    Potential.addWallRepulsion(force, attacker)
                    Potential.addCollisionRepulsion(force, attacker)
                }
            }
            if (!isFlying) {
                Potential.addChokeRepulsion(force, attacker)
            }
            if (!myCluster.enemyHullWithBuffer.isEmpty) {
                val ppd = PointPairDistance()
                DistanceToPoint.computeDistance(myCluster.enemyHullWithBuffer.boundary, position.toCoordinate(), ppd)
                val dx = (ppd.getCoordinate(0).toPosition() - position).toVector().setLength(1.8f)
                force.add(dx)
            }

//            Potential.addThreatRepulsion(force, attacker)
            if (force.len() > 0.9) {
                val pos = Potential.wrapUp(attacker, force).asValidPosition()
                if (attacker.targetPosition.getDistance(pos) > 16) {
                    attacker.move(pos)
                }
            }
            MyInfo.unitStatus[attacker] = "Retreat/Safe"
        }

        return TaskStatus.RUNNING
    }
}

class CombatCluster(val cluster: Cluster<PlayerUnit>) : Task() {
    override val utility: Double = 1.0

    private val enemies = mutableListOf<PlayerUnit>()

    private val attackerTasks = ManagedTaskProvider({
        cluster.myUnits
                .asSequence()
                .filterIsInstance<Attacker>()
                .filter { it !is Worker }
                .toList()
    }) {
        ManageAttacker(it, enemies)
    }

    override fun processInternal(): TaskStatus {
        val buddies = cluster.mySimUnits

        enemies.clear()
        enemies.addAll(cluster.enemyUnits.filter { it.isDetected })

        enemies.sortByDescending {
            if (it !is Attacker) return@sortByDescending 0.0
            val thisOne = cluster.enemySimUnits.firstOrNull { e -> (e.userObject as PlayerUnit).id == it.id }
                    ?: return@sortByDescending 0.0
            val type = (thisOne.userObject as PlayerUnit).type
            if (type.isBuilding && type.groundWeapon() == WeaponType.None && type.airWeapon() == WeaponType.None) return@sortByDescending 0.0
            CombatEval.probabilityToWin(buddies, cluster.enemySimUnits - thisOne, 0.8f) +
                    (if (it is Worker) 0.02 else 0.0)
        }
        return processAll(attackerTasks)
    }
}


class CombatController : Task() {
    override val utility: Double = 1.0

    private val clusters = ParallelTask(ManagedTaskProvider({ Cluster.clusters }, {
        CombatCluster(it)
    }))

    override fun processInternal(): TaskStatus = clusters.process()

    companion object : TaskProvider {
        private val tasks = listOf(CombatController().nvr())
        override fun invoke(): List<Task> = tasks
    }
}