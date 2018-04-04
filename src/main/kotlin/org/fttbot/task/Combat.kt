package org.fttbot.task

import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.Parallel.Companion.parallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.info.*
import org.fttbot.task.Actions.flee
import org.fttbot.task.Actions.reach
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import kotlin.math.max
import kotlin.math.min

object Combat {
    fun attack(units: List<PlayerUnit>, targets: List<PlayerUnit>): Node<Any, Any> {
        return sequence(DispatchParallel({ units.filterIsInstance(MobileUnit::class.java) }, "attack") { unit ->
            val simUnit = SimUnit.of(unit)
            var bestTarget: PlayerUnit? = null

            sequence<Any, AttackBoard>({ AttackBoard(unit) },
                    Inline("Determine best target") {
                        bestTarget = targets.filter {
                            unit as Attacker
                            it.isDetected && unit.getWeaponAgainst(it).type() != WeaponType.None
                        }.minBy {
                            val enemySim = SimUnit.of(it)
                            (it.hitPoints + it.shields) / max(simUnit.damagePerFrameTo(enemySim), 0.01) +
                                    (if (it is Larva || it is Egg) 5000 else 0) +
                                    (if (it is Worker) -100 else 0) +
                                    2 * it.getDistance(unit) +
                                    (if (it.canAttack(unit))
                                        -enemySim.damagePerFrameTo(simUnit)
                                    else
                                        0.0) * 500 +
                                    (if (it is Attacker)
                                        -200.0
                                    else 0.0) +
                                    (if (it.isDetected || !it.exists()) 0 else 500)
                        }
                        if (bestTarget == null)
                            NodeStatus.FAILED
                        else {
                            this!!.target = bestTarget
                            NodeStatus.SUCCEEDED
                        }
                    },
                    attack()
            )
        }, Sleep)
    }

    data class AttackBoard(val unit: MobileUnit, var target: PlayerUnit? = null)

    fun attack() =
            fallback<AttackBoard>(
                    Condition("Target destroyed?") {
                        this!!
                        !target!!.exists() && !EnemyInfo.seenUnits.contains(target!!)
                    },
                    Delegate {
                        this!!
                        fleeFromStorm(unit)
                    },
                    sequence(
                            fallback(
                                    Condition("Can I see you?") { this!!; target!!.isVisible },
                                    Delegate { this!!; reach(unit, target!!.lastKnownPosition, 100) }
                            ),
                            fallback(
                                    sequence(
                                            Condition("Unit is Lurker?") { this!!; unit is Lurker && unit.canAttack(target!!) }, Delegate { this!!; BurrowCommand(unit) }
                                    ),
                                    Condition("Already attacking that?") {
                                        this!!
                                        unit.targetUnit == target
                                    },
                                    Delegate {
                                        this!!
                                        AttackCommand(unit, target!!)
                                    }
                            )
                    )
            )

    private fun fleeFromStorm(unit: MobileUnit): Node<Any, Any> {
        return sequence(
                Condition("Avoid storm?") { unit.isUnderStorm },
                Condition("Air unit?") { unit.isFlying },
                Delegate {
                    val storm = FTTBot.game.bullets.minBy { unit.getDistance(it.position) } ?: return@Delegate Fail
                    val targetPosition = unit.position.minus(storm.position).toVector().setLength(32f).add(unit.position.toVector()).toPosition()
                    MoveCommand(unit, targetPosition)
                }
        )
    }

    fun defending(): Node<Any, Any> = fallback(
            DispatchParallel({ MyInfo.myBases }) {
                sequence<Any, WorkerDefenseBoard>({ WorkerDefenseBoard() },
                        WorkerDefense((it as PlayerUnit).position),
                        parallel(1,
                                Delegate {
                                    this!!
                                    attack(defendingWorkers, enemies)
                                }, Sleep(12)),
                        Sleep
                )
            }, Sleep
    )


    fun attacking(): Node<Any, Any> = fallback(
            DispatchParallel({ Cluster.mobileCombatUnits }) { myCluster ->
                var enemies: List<PlayerUnit> = emptyList()
                var allies: Position? = null
                fallback(
                        sequence(
                                Inline("Find me some enemies") {
                                    enemies = Cluster.enemyClusters.minBy {
                                        val distance = MyInfo.myBases.map { base -> base as PlayerUnit; base.getDistance(it.position) }.min()
                                                ?: Double.MAX_VALUE
                                        val eval = CombatEval.probabilityToWin(myCluster.units.map { SimUnit.of(it) }, it.units.filter { it is Attacker }.map { SimUnit.of(it) })
                                        it.position.getDistance(myCluster.position) - 500 * eval +
                                                min(distance / 5.0, 300.0)

                                    }?.units?.toList() ?: return@Inline NodeStatus.RUNNING
                                    NodeStatus.SUCCEEDED
                                },
                                Condition("Good combat eval?") {
                                    val combatEval = ClusterUnitInfo.getInfo(myCluster).combatEval
                                    combatEval > 0.55 ||
                                            combatEval > 0.45 && myCluster.units.any { me -> enemies.any { it.canAttack(me) } }

                                },
                                Delegate {
                                    val units = myCluster.units.toMutableList()
                                    units.retainAll(Board.resources.units)
                                    Board.resources.reserveUnits(units)
                                    attack(units, enemies)
                                }
                        ),
                        DispatchParallel({ myCluster.units }) {
                            flee(it)
                        },
                        sequence(
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
        var enemyCluster: Cluster<PlayerUnit>?
        var myCluster: Cluster<PlayerUnit>?
        return Condition("Should $at be defended?") {
            enemyCluster = Cluster.enemyClusters.filter {
                it.units.any { it is MobileUnit && it is Attacker }
            }.minBy { it.position.getDistance(at) } ?: return@Condition false
            myCluster = Cluster.myClusters.minBy { it.position.getDistance(at) } ?: return@Condition false
            val combatEval = CombatEval.probabilityToWin(myCluster!!.units.filter { it is Attacker }.map { SimUnit.of(it) },
                    enemyCluster!!.units.filter { it is Attacker && it is MobileUnit }.map { SimUnit.of(it) })
            val distanceFactor = myCluster!!.position.getDistance(enemyCluster!!.position) * 0.00025
            combatEval < 0.5 - distanceFactor
        }
    }
}