package org.fttbot.task

import bwem.ChokePoint
import org.fttbot.*
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.info.*
import org.fttbot.task.Actions.flee
import org.fttbot.task.Actions.reach
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.WalkPosition
import org.openbw.bwapi4j.unit.*
import kotlin.math.max

object Combat {
    fun attack(units: List<PlayerUnit>, targets: List<PlayerUnit>): Node<Any, Any> {
        return Sequence(DispatchParallel({ units.filterIsInstance(MobileUnit::class.java) }, "attack") { unit ->
            val simUnit = SimUnit.of(unit)
            var bestTarget: PlayerUnit? = null

            Sequence(
                    Inline("Determine best target") {
                        bestTarget = targets.minBy {
                            val enemySim = SimUnit.of(it)
                            (it.hitPoints + it.shields) / max(simUnit.damagePerFrameTo(enemySim), 0.01) +
                                    (if (it is Larva || it is Egg) 5000 else 0) +
                                    (if (it is Worker) -100 else 0)
                            (if (!it.isDetected) 5000 else 0) +
                                    2 * it.getDistance(unit) +
                                    2 * (MyInfo.myBases.map { base -> base as PlayerUnit; base.getDistance(it) }.min()
                                    ?: 0) +
                                    (if (it.canAttack(unit))
                                        -enemySim.damagePerFrameTo(simUnit)
                                    else
                                        enemySim.damagePerFrameTo(simUnit)) * 500
                        }
                        if (bestTarget == null)
                            NodeStatus.FAILED
                        else
                            NodeStatus.SUCCEEDED
                    },
                    Delegate { attack(unit, bestTarget!!) }
            )
        }, Sleep)
    }

    fun attack(unit: MobileUnit, target: PlayerUnit) =
            Fallback(
                    Condition("$target destroyed?") { !target.exists() && !EnemyInfo.seenUnits.contains(target) },
                    fleeFromStorm(unit),
                    Sequence(
                            Fallback(
                                    Condition("Can I see you?") { target.isVisible },
                                    Delegate { reach(unit, target.lastKnownPosition, 100) }
                            ),
                            Fallback(
                                    Sequence(
                                            Condition("$unit is Lurker") { unit is Lurker && unit.canAttack(target) }, Delegate { BurrowCommand(unit) }
                                    ),
                                    Condition("Already attacking that?") {
                                        unit.targetUnit == target
                                    },
                                    Delegate {
                                        AttackCommand(unit, target)
                                    }
                            )
                    )
            )

    private fun fleeFromStorm(unit: MobileUnit): Node<Any, Any> {
        return Sequence(
                Condition("Avoid storm?") { unit.isUnderStorm },
                Condition("Air unit?") { unit.isFlying },
                Delegate {
                    val storm = FTTBot.game.bullets.minBy { unit.getDistance(it.position) } ?: return@Delegate Fail
                    val targetPosition = unit.position.minus(storm.position).toVector().setLength(32f).add(unit.position.toVector()).toPosition()
                    MoveCommand(unit, targetPosition)
                }
        )
    }

    fun moveToStandOffPosition(units: List<MobileUnit>, targetPosition: Position): Node<Any, Any> {
        if (units.isEmpty()) return Success
        val path = FTTBot.bwem.getPath(FTTBot.self.startLocation.toPosition(), targetPosition)
        if (path.isEmpty) return Fail
        val cpIndex = path.indexOfFirst { cp ->
            !UnitQuery.enemyUnits.inRadius(cp.center.toPosition(), 300).isEmpty()
        }
        if (cpIndex <= 0)
            return Fail
        val cpCenter = path[cpIndex - 1].center
        val actualTarget =
                if (cpIndex == 1)
                    cpCenter
                else {
                    val adjacentAreas = path[cpIndex - 2].areas
                    val aa = adjacentAreas.first.walkPositionWithHighestAltitude
                    val ab = adjacentAreas.second.walkPositionWithHighestAltitude
                    if (aa.toPosition().getDistance(targetPosition) < ab.toPosition().getDistance(targetPosition))
                        cpCenter.add(aa).divide(WalkPosition(2, 2))
                    else
                        cpCenter.add(ab).divide(WalkPosition(2, 2))
                }
        return DispatchParallel({ units }, "moveToStandOff") {
            reach(it, actualTarget.toPosition(), 96)
        }
    }

    fun considerWorkerDefense(units: List<Worker>, targets: List<PlayerUnit>): Node<Any, Any> {
        return attack(units, targets)
    }

    fun defendPosition(units: List<MobileUnit>, position: Position, against: Position): Node<Any, Any> {
        val defensePosition = findGoodDefenseChokePoint(position, against)?.center?.toPosition() ?: return Fail
        var enemies = emptyList<PlayerUnit>()
        return Fallback(
                Sequence(
                        Inline("Find enemies") {
                            enemies = UnitQuery.enemyUnits.inRadius(defensePosition, 300)
                            if (enemies.isEmpty()) NodeStatus.FAILED else NodeStatus.SUCCEEDED
                        },
                        Delegate {
                            attack(units, enemies)
                        }
                ),
                DispatchParallel({ units }, "defendPosition") {
                    reach(it, defensePosition, 64)
                }
        )
    }

    fun findGoodDefenseChokePoint(near: Position, against: Position): ChokePoint? {
        val consideredCPs = FTTBot.bwem.getPath(near, against)
                .filter { !it.isBlocked }
                .takeWhile { near.getDistance(it.center.toPosition()) < 1000 }
        val bestCP = consideredCPs
                .withIndex()
                .minBy { it.value.geometry.size } ?: return null
        return bestCP.value
    }

    fun defending() : Node<Any, Any> = Fallback (
            DispatchParallel({MyInfo.myBases}) {
                WorkerDefense((it as PlayerUnit).position)
            }, Sleep
    )


    fun attacking(): Node<Any, Any> = Fallback(
            DispatchParallel({ Cluster.mobileCombatUnits }) { myCluster ->
                var enemies: List<PlayerUnit> = emptyList()
                var allies: Position? = null
                Fallback(
                        Sequence(
                                Inline("Find me some enemies") {
                                    enemies = Cluster.enemyClusters.minBy { it.position.getDistance(myCluster.position) }?.units?.toList() ?: return@Inline NodeStatus.RUNNING
                                    NodeStatus.SUCCEEDED
                                },
                                Condition("Good combat eval?") {
                                    val combatEval = ClusterUnitInfo.getInfo(myCluster).combatEval
                                    combatEval > 0.55 ||
                                            combatEval > 0.45 && myCluster.units.any { it.isAttacking }

                                },
                                Delegate {
                                    attack(myCluster.units.toList(), enemies)
                                }
                        ),
                        DispatchParallel({myCluster.units}) {
                            flee(it)
                        },
                        Sequence(
                                Inline("Find me some allies") {
                                    allies = Cluster.mobileCombatUnits.filter { it != myCluster }
                                            .minBy { it.position.getDistance(myCluster.position) }?.position
                                            ?: if (!MyInfo.myBases.isEmpty()) (MyInfo.myBases[0] as PlayerUnit).position else return@Inline NodeStatus.RUNNING
                                    NodeStatus.SUCCEEDED
                                },
                                Delegate {
                                    reach(myCluster.units.toList(), allies!!)
                                }
                        ),
                        Sleep)
            },
            Sleep
    )

    fun shouldIncreaseDefense(at: Position): Node<Any, Any> {
        var enemyCluster: Cluster<PlayerUnit>? = null
        var myCluster: Cluster<PlayerUnit>? = null
        return Condition("Should $at be defended?") {
            enemyCluster = Cluster.enemyClusters.filter {
                it.units.any { it is MobileUnit && it is Attacker }
            }.minBy { it.position.getDistance(at) } ?: return@Condition false
            myCluster = Cluster.myClusters.minBy { it.position.getDistance(at) } ?: return@Condition false
            val combatEval = CombatEval.probabilityToWin(myCluster!!.units.filter { it is Attacker }.map { SimUnit.of(it) },
                    enemyCluster!!.units.filter { it is Attacker && it is MobileUnit }.map { SimUnit.of(it) })
            val distanceFactor= myCluster!!.position.getDistance(enemyCluster!!.position) * 0.00025
            combatEval < 0.5 - distanceFactor
        }
    }
}