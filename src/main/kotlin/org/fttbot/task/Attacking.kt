package org.fttbot.task

import com.badlogic.gdx.math.Vector2
import org.fttbot.Potential
import org.fttbot.ResourcesBoard
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.info.*
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker
import java.util.*

class Attack(val unit: PlayerUnit, var target: PlayerUnit? = null) : Task() {
    override val utility: Double = 1.0

    override fun processInternal(): TaskStatus {
        if (target == null || !target!!.exists()) return TaskStatus.FAILED
        if (unit.orderTarget != target) {
            (unit as Attacker).attack(target)
        }
        return TaskStatus.RUNNING
    }

}

class ManageAttacker(val attacker: Attacker) : Task() {
    override val utility: Double
        get() = 1.0

    override fun processInternal(): TaskStatus {
        if (attacker is Worker) return TaskStatus.RUNNING
        val myCluster = attacker.myCluster
        val force = Vector2()
        val id = attacker.id
        val isFlying = attacker.isFlying
        val position = attacker.position
        val targetUnit = attacker.targetUnit

        if (attacker is MobileUnit) {
            if (myCluster.enemyUnits.isNotEmpty()) {
                if (myCluster.attackEval.second >= 0.6 && myCluster.attackEval.first.any { it.id == id }) {
                    ResourcesBoard.reserveUnit(attacker)
                    if (!attacker.canMoveWithoutBreakingAttack) return TaskStatus.RUNNING

                    val buddies = UnitQuery.myUnits.inRadius(attacker, 400)
                    val candidates = (UnitQuery.enemyUnits + EnemyInfo.seenUnits)
                            .filter { attacker.hasWeaponAgainst(it) && it.isDetected }
                    val bestEnemyToKill = CombatEval.bestEnemyToKill(buddies.map(SimUnit.Companion::of),
                            candidates.filter { attacker.canAttack(it, (attacker.topSpeed * 2).toInt()) }.map(SimUnit.Companion::of))
                    val target = bestEnemyToKill?.let { candidates.firstOrNull { it.id == bestEnemyToKill.id } }
                            ?: candidates.closestTo(attacker)
                            ?: return TaskStatus.RUNNING
                    if (targetUnit != target && target.isVisible) {
                        if (attacker.canAttack(target, 48))
                            attacker.attack(target)
                        else
                            attacker.move(EnemyInfo.predictedPositionOf(target, attacker.getDistance(target) / attacker.topSpeed))
                    } else if (attacker.targetPosition.getDistance(target.position) > 192) {
                        attacker.move(target.position)
                    }
                    return TaskStatus.RUNNING
                }
            } else {
                Cluster.clusters
                        .asSequence()
                        .filter { it.enemyUnits.isNotEmpty() }
                        .mapNotNull {
                            val eval = CombatEval.probabilityToWin((myCluster.mySimUnits + it.mySimUnits).filter { it.isAttacker },
                                    it.enemySimUnits.filter { it.isAttacker })
                            if (eval < 0.7) null else it to eval
                        }.minBy { it.second }?.let { (target, _) ->
                            val targetPosition = target.enemyUnits.minBy { it.getDistance(attacker) }!!.position
                            if (attacker.targetPosition.getDistance(targetPosition) > 192) {
                                attacker.move(targetPosition)
                            }
                            return TaskStatus.RUNNING
                        }

            }
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
                val bestCluster = Cluster.clusters
                        .asSequence()
                        .filter { it.attackEval.second > 0.6 && it != myCluster }
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
            }
            if (!isFlying) {
                Potential.addWallRepulsion(force, attacker)
                Potential.addChokeRepulsion(force, attacker)
                Potential.addCollisionRepulsion(force, attacker)
            }
            Potential.addThreatRepulsion(force, attacker)
            if (force.len() > 0.7) {
                val pos = Potential.wrapUp(attacker, force)
                if (attacker.targetPosition.getDistance(pos) > 16) {
                    attacker.move(pos)
                }
            }
        }

        return TaskStatus.RUNNING
    }
}

class CombatController() : Task() {
    private val rng = SplittableRandom()
    override val utility: Double = 1.0

    private val attackerTasks = ManagedTaskProvider({ ResourcesBoard.units.filterIsInstance<Attacker>() }, {
        ManageAttacker(it)
    })

    override fun processInternal(): TaskStatus = processAll(attackerTasks)

    companion object : TaskProvider {
        private val tasks = listOf(CombatController().neverFail().repeat())
        override fun invoke(): List<Task> = tasks
    }
}