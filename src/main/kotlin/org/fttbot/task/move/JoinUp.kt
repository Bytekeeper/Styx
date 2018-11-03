package org.fttbot.task.move

import org.fttbot.Commands
import org.fttbot.estimation.CombatEval
import org.fttbot.info.*
import org.fttbot.minus
import org.fttbot.path
import org.fttbot.task.Action
import org.fttbot.task.LazyTask
import org.fttbot.task.TaskStatus
import org.fttbot.toVector
import org.openbw.bwapi4j.unit.*

class JoinUp(val unit: MobileUnit) : Action() {
    private val avoidCombat by LazyTask { AvoidCombat(unit) }

    override fun processInternal(): TaskStatus {
        avoidCombat.process().andWhenRunning { return TaskStatus.DONE }

        val myCluster = unit.myCluster
        val position = unit.position

        val clustersWithEnemies = Cluster.clusters.asSequence()
                .map { it.userObject as Cluster<PlayerUnit> }
                .filter { it.enemyUnits.isNotEmpty() && it != myCluster }

        clustersWithEnemies
                .asSequence()
                .filter { c ->
                    val cDistance = c.position.getDistance(position)
                    val cDir = c.position.minus(position).toVector().nor()
                    clustersWithEnemies.none { o ->
                        val otherPosition = o.position
                        otherPosition.getDistance(position) < cDistance &&
                                otherPosition.minus(position).toVector().nor().dot(cDir) > 0.7f
                    }
                }.mapNotNull {
                    val enemies = it.enemySimUnits.filter { (it.userObject as PlayerUnit).isCombatRelevant() }
                    if (enemies.isEmpty() && it.enemyUnits.any { unit is Attacker && unit.hasWeaponAgainst(it) })
                        it to 1.0
                    else {
                        val eval = CombatEval.probabilityToWin((myCluster.mySimUnits + it.mySimUnits).filter { (it.userObject as PlayerUnit).isCombatRelevant() && it.userObject !is Worker },
                                enemies)
                        if (eval < 0.5 && it.myUnits.none { u -> u is Building }) null else it to eval
                    }
                }.minBy { it.second * it.first.position.getDistance(position) }
                ?.let { (target, eval) ->
                    val targetPosition = target.enemyUnits.minBy { it.getDistance(unit) }!!.position
                    if (unit.targetPosition.getDistance(position) < 64 && targetPosition.getDistance(position) > 96) {
                        MyInfo.unitStatus[unit] = "Join/Engage"
                        if (unit.isFlying)
                            Commands.move(unit, targetPosition)
                        else {
                            val waypoint = path(position, targetPosition).path
                                    .map { it.center.toPosition() }
                                    .firstOrNull { it.getDistance(position) > 64 }
                                    ?: targetPosition
                            Commands.move(unit, waypoint)
                        }
                        return TaskStatus.RUNNING
                    }
                }
        return TaskStatus.DONE
    }
}