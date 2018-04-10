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

data class DefendersBoard(var relevantEnemies: List<PlayerUnit> = emptyList())

object Combat {
    fun attack(units: List<PlayerUnit>, targets: List<PlayerUnit>): Node<Any, Any> {
        if (units.isEmpty()) {
            return Fail
        }
        if (targets.isEmpty()) {
            return Success
        }
        return sequence(DispatchParallel<Any, MobileUnit>("Attack", { units.filterIsInstance(MobileUnit::class.java) }) { unit ->
            val simUnit = SimUnit.of(unit)
            var bestTarget: PlayerUnit?

            fallback(
                    sequence({ AttackBoard(unit) },

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
                                            (if (it.canAttack(unit, 32))
                                                -enemySim.damagePerFrameTo(simUnit)
                                            else
                                                0.0) * 800 +
                                            (if (it is Attacker)
                                                -200.0
                                            else 0.0) +
                                            (if (it.isDetected || !it.exists()) 0 else 500) +
                                            (if (it is Addon) 8000 else 0)
                                }
                                if (bestTarget == null)
                                    NodeStatus.FAILED
                                else {
                                    this!!.target = bestTarget
                                    NodeStatus.SUCCEEDED
                                }
                            },
                            attack()
                    ),
                    flee(unit),
                    Sleep
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
            DispatchParallel<Any, Base>("Defending", { MyInfo.myBases }) { base ->
                base as PlayerUnit
                parallel(1000,
                        sequence({ WorkerDefenseBoard() },
                                fallback(
                                        WorkerDefense(base.position),
                                        DispatchParallel<WorkerDefenseBoard, Worker>("Flee you worker fools", { this!!.defendingWorkers }) {
                                            flee(it)
                                        }
                                ),
                                parallel(1,
                                        Delegate {
                                            this!!
                                            attack(defendingWorkers, enemies)
                                        }, Sleep(12)),
                                Sleep
                        ),
                        fallback(
                                sequence<Any, DefendersBoard>({ DefendersBoard() },
                                        Condition("Attackers close to base?") {
                                            this!!
                                            val enemyCluster = Cluster.enemyClusters.minBy { base.getDistance(it.position) }
                                                    ?: return@Condition false
                                            relevantEnemies = enemyCluster.units.filter { enemy ->
                                                UnitQuery.myBuildings.any { it.getDistance(base) < 300 && enemy.canAttack(it, 32) }
                                            }
                                            !relevantEnemies.isEmpty()
                                        },
                                        Delegate {
                                            this!!
                                            val myUnits = Cluster.mobileCombatUnits.minBy { base.getDistance(it.position) }
                                                    ?: return@Delegate Fail
                                            attack(myUnits.units.toList(), relevantEnemies)
                                        }
                                ),
                                Sleep
                        )
                )
            }, Sleep
    )


    fun attacking(): Node<Any, Any> = fallback(
            DispatchParallel<Any, Cluster<MobileUnit>>("Attacking", { Cluster.mobileCombatUnits }) { myCluster ->
                var enemies: List<PlayerUnit> = emptyList()
                var allies: Position? = null
                fallback(
                        sequence(
                                Inline("Find me some enemies") {
                                    enemies = Cluster.enemyClusters.minBy {
                                        val distance = MyInfo.myBases.map { base -> base as PlayerUnit; base.getDistance(it.position) }.min()
                                                ?: Double.MAX_VALUE
                                        val eval = CombatEval.probabilityToWin(myCluster.units.map { SimUnit.of(it) }, it.units.filter { it is Attacker && it.isCompleted }.map { SimUnit.of(it) })
                                        it.position.getDistance(myCluster.position) - 500 * eval +
                                                min(distance / 5.0, 300.0)

                                    }?.units?.toList() ?: return@Inline NodeStatus.RUNNING
                                    NodeStatus.SUCCEEDED
                                },
                                Condition("Good combat eval?") {
                                    val combatEval = ClusterUnitInfo.getInfo(myCluster).combatEval
                                    combatEval > 0.55 ||
                                            combatEval > 0.30 && myCluster.units.any { me -> enemies.any { it.canAttack(me) } }
                                },
                                Delegate {
                                    val units = myCluster.units.toMutableList()
                                    units.retainAll(Board.resources.units)
                                    Board.resources.reserveUnits(units)
                                    attack(units, enemies)
                                }
                        ),
                        DispatchParallel<Any, MobileUnit>("Flee", { myCluster.units }) {
                            flee(it)
                        },
                        sequence(
                                Inline("Find me some allies") {
                                    allies = Cluster.mobileCombatUnits.filter {
                                        it != myCluster &&
                                                FTTBot.bwem.data.getMiniTile(it.position.toWalkPosition()).isWalkable
                                    }.minBy { it.position.getDistance(myCluster.position) }?.position
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
            val combatEval = CombatEval.probabilityToWin(myCluster!!.units.filter { it is Attacker && it !is Worker }.map { SimUnit.of(it) },
                    enemyCluster!!.units.filter { it is Attacker && it is MobileUnit && it.isCompleted }.map { SimUnit.of(it) })
            val distanceFactor = myCluster!!.position.getDistance(enemyCluster!!.position) * 0.0003
            combatEval < 0.7 - distanceFactor
        }
    }
}