package org.fttbot.task

import com.badlogic.gdx.math.Vector2
import org.fttbot.Potential
import org.fttbot.estimation.CombatEval
import org.fttbot.info.*
import org.fttbot.minus
import org.fttbot.toPosition
import org.fttbot.toVector
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker

class ManageAttacker(val attacker: Attacker, val myCluster: Cluster<PlayerUnit>, val enemies: List<PlayerUnit>) : Task() {
    override val utility: Double
        get() = 1.0

    override fun processInternal(): TaskStatus {
        val id = attacker.id
        val isFlying = attacker.isFlying
        val position = attacker.position
        val targetUnit = attacker.targetUnit

        if (attacker is MobileUnit) {
            if (myCluster.enemyUnits.isNotEmpty()) {
                if (myCluster.attackEval.second >= 0.6 && myCluster.attackEval.first.any { it.id == id }) {
                    if (!attacker.canMoveWithoutBreakingAttack)
                        return TaskStatus.RUNNING

                    val candidates = enemies
                            .filter { attacker.hasWeaponAgainst(it) && it.isDetected }

                    val target = candidates.mapIndexed { index, playerUnit ->
                        playerUnit to attacker.getDistance(playerUnit) + index * 16 -
                                (if (targetUnit == playerUnit) 60 else 0)
                    }.minBy { it.second }?.first ?: return TaskStatus.RUNNING
                    if (targetUnit != target && target.isVisible) {
                        if (attacker.canAttack(target, 48))
                            attacker.attack(target)
                        else
                            attacker.move(EnemyInfo.predictedPositionOf(target, attacker.getDistance(target) / attacker.topSpeed))
                    } else if (attacker.targetPosition.getDistance(target.position) > 192) {
                        attacker.move(target.position)
                    }
                    return TaskStatus.RUNNING
                } else {
                    val x = 2
                }
            } else {
                Cluster.clusters
                        .asSequence()
                        .filter { it.enemyUnits.isNotEmpty() }
                        .mapNotNull {
                            val enemies = it.enemySimUnits.filter { it.isAttacker }
                            if (enemies.isEmpty() && it.enemyUnits.any { attacker.hasWeaponAgainst(it) })
                                it to 1.0
                            else {
                                val eval = CombatEval.probabilityToWin((myCluster.mySimUnits + it.mySimUnits).filter { it.isAttacker },
                                        enemies)
                                if (eval < 0.6) null else it to eval
                            }
                        }.minBy { it.second * it.first.position.getDistance(position) }?.let { (target, _) ->
                            val targetPosition = target.enemyUnits.minBy { it.getDistance(attacker) }!!.position
                            if (attacker.targetPosition.getDistance(targetPosition) > 192) {
                                attacker.move(targetPosition)
                            }
                            return TaskStatus.RUNNING
                        }

            }

            val force = Vector2()
            val threats = attacker.potentialAttackers(32)
            if (threats.isEmpty()) {
                (UnitQuery.enemyUnits.inRadius(attacker, 400) + EnemyInfo.seenUnits.filter { it.getDistance(attacker) <= 400 })
                        .filter {
                            attacker.hasWeaponAgainst(it) && it.isDetected &&
                                    (myCluster.attackEval.second >= 0.5 || it !is Attacker || it.maxRangeVs(attacker) < attacker.maxRangeVs(it))
                        }
                        .minBy { it.getDistance(attacker) }
                        ?.let {
                            if (targetUnit != it && it.isVisible) {
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
            myCluster.enemyHullWithBuffer.coordinates.minBy { it.toPosition().getDistance(position) }?.toPosition()
                    ?.let {
                        val dx = (it - position).toVector().setLength(1.2f)
                        force.add(dx)
                    }

//            Potential.addThreatRepulsion(force, attacker)
            if (force.len() > 0.9) {
                val pos = Potential.wrapUp(attacker, force)
                if (attacker.targetPosition.getDistance(pos) > 16) {
                    attacker.move(pos)
                }
            }
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
        ManageAttacker(it, cluster, enemies)
    }

    override fun processInternal(): TaskStatus {
        val buddies = cluster.mySimUnits

        enemies.clear()
        enemies.addAll(cluster.enemyUnits.filter { it.isDetected })

        enemies.sortByDescending {
            if (it !is Attacker) return@sortByDescending 0.0
            val thisOne = cluster.enemySimUnits.firstOrNull { e -> e.id == it.id }
                    ?: return@sortByDescending 0.0
            CombatEval.probabilityToWin(buddies, cluster.enemySimUnits - thisOne, 0.8f) +
                    (if (it is Worker) 0.2 else 0.0)
        }
        return processAll(attackerTasks)
    }
}


class CombatController : Task() {
    override val utility: Double = 1.0

    private val combatClusterTasks = ManagedTaskProvider({ Cluster.clusters }, {
        CombatCluster(it)
    })

    private val freeSquadTask = ManagedTaskProvider({ Cluster.squads }) {
        CombatCluster(it)
    }

    override fun processInternal(): TaskStatus {
        return processAll(*(combatClusterTasks() + freeSquadTask()).toTypedArray())
    }

    companion object : TaskProvider {
        private val tasks = listOf(CombatController().nvr())
        override fun invoke(): List<Task> = tasks
    }
}