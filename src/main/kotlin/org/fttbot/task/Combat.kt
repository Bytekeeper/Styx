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
import org.openbw.bwapi4j.type.Order
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import kotlin.math.max
import kotlin.math.min

data class AttackersBoard(var attackers: List<PlayerUnit> = emptyList(), var enemies: List<PlayerUnit> = emptyList())
data class AttackBoard(val unit: MobileUnit, var target: PlayerUnit? = null)

object Combat {
    fun attack(board: AttackersBoard): Node {
        return sequence(DispatchParallel("Attack", { board.attackers.filterIsInstance(MobileUnit::class.java) }) { unit ->
            val childBoard = AttackBoard(unit)

            fallback(
                    sequence(
                            Inline("Determine best target") {
                                childBoard.target = findBestTarget(board.enemies, unit)
                                if (childBoard.target == null)
                                    NodeStatus.FAILED
                                else
                                    NodeStatus.SUCCEEDED
                            },
                            attack(unit, childBoard)
                    ),
                    flee(unit),
                    Sleep
            )
        }, Sleep)
    }

    private fun findBestTarget(targets: List<PlayerUnit>, unit: MobileUnit): PlayerUnit? {
        val simUnit = SimUnit.of(unit)
        return targets.filter {
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
                        0.0) * 900 +
                    (if (it is Attacker) -200.0 else 0.0) +
                    (if (it.isDetected || !it.exists()) 0 else 500) +
                    (if (it is Addon) 8000 else 0) +
                    (if (unit.canAttack(it)) {
                        if (unit is Lurker && unit.isBurrowed) -1000 else -300
                    } else 0) +
                    (if (unit.isFasterThan(it)) -200 else 200)
        }
    }


    fun attack(unit: MobileUnit, board: AttackBoard): Node {
        val reachEnemyBoard = ReachBoard(tolerance = 100)
        return fallback(
                Condition("Target destroyed?") {
                    !board.target!!.exists() && !EnemyInfo.seenUnits.contains(board.target!!)
                },
                fleeFromStorm(unit),
                sequence(
                        fallback(
                                Condition("Can I see you?") { board.target!!.isVisible },
                                sequence(
                                        Inline("Find enemy") {
                                            reachEnemyBoard.position = board.target!!.lastKnownPosition
                                            NodeStatus.SUCCEEDED
                                        },
                                        reach(unit, reachEnemyBoard)
                                )
                        ),
                        moveIntoAttackRange(unit, board),
                        fallback(
                                prepareUnitForAttack(unit, board),
                                Condition("Already attacking that?") {
                                    unit.targetUnit == board.target
                                },
                                Delegate({ unit.targetUnit != board.target!! }) {
                                    AttackCommand(unit, board.target!!)
                                }
                        )
                )
        )
    }

    private fun prepareUnitForAttack(unit: MobileUnit, board: AttackBoard): Sequence {
        return sequence(
                Condition("Unit is Lurker?") { unit is Lurker && !unit.isBurrowed && unit.canAttack(board.target!!) },
                fallback(
                        Condition("Unit is burrowing?") { unit.order == Order.Burrowing },
                        BurrowCommand(unit)
                )
        )
    }

    private fun moveIntoAttackRange(unit: MobileUnit, board: AttackBoard): Fallback {
        val reachBoard = ReachBoard(tolerance = 8)
        return fallback(
                Condition("Close enough?") {
                    val range = (unit as Attacker).maxRangeVs(board.target!!).toDouble() * (if (unit is Lurker && !unit.isBurrowed) 0.7 else 1.0)
                    range >= unit.getDistance(board.target)
                },
                sequence(
                        Inline("Update reach position to ${board.target?.position}") {
                            reachBoard.position = board.target!!.position
                            NodeStatus.SUCCEEDED
                        },
                        reach(unit, reachBoard)
                )
        )
    }

    private fun fleeFromStorm(unit: MobileUnit): Node {
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

    fun defending(): Node = fallback(
            DispatchParallel("Defending", { MyInfo.myBases }) { base ->
                val defendersBoard = AttackersBoard()
                base as PlayerUnit
                parallel(1000,
                        workerDefense(base),
                        fallback(
                                sequence(
                                        Condition("Attackers close to base?") {
                                            val enemyCluster = Cluster.enemyClusters.minBy { base.getDistance(it.position) }
                                                    ?: return@Condition false
                                            defendersBoard.enemies = enemyCluster.units.filter { enemy ->
                                                UnitQuery.myBuildings.any { it.getDistance(base) < 300 && enemy.canAttack(it, 32) }
                                            }
                                            !defendersBoard.enemies.isEmpty()
                                        },
                                        sequence(
                                                Inline("Who should attack?") {
                                                    val myUnits = Cluster.mobileCombatUnits.minBy { base.getDistance(it.position) }
                                                            ?: return@Inline NodeStatus.FAILED
                                                    val unitsAvailable = myUnits.units.filter { Board.resources.units.contains(it) }
                                                    defendersBoard.attackers = unitsAvailable
                                                    Board.resources.reserveUnits(unitsAvailable)
                                                    NodeStatus.SUCCEEDED
                                                },
                                                attack(defendersBoard)
                                        )
                                ),
                                Sleep
                        )
                )
            }, Sleep
    )

    private fun workerDefense(base: PlayerUnit): Sequence {
        val workerDefenseBoard = WorkerDefenseBoard()
        val workerAttackersBoard = AttackersBoard()
        return sequence(
                fallback(
                        WorkerDefense(base.position, workerDefenseBoard),
                        DispatchParallel("Flee you worker fools", { workerDefenseBoard.defendingWorkers }) {
                            flee(it)
                        }
                ),
                parallel(1,
                        Inline("Assign workers to defend") {
                            workerAttackersBoard.attackers = workerDefenseBoard.defendingWorkers
                            workerAttackersBoard.enemies = workerDefenseBoard.enemies
                            NodeStatus.SUCCEEDED
                        },
                        attack(workerAttackersBoard),
                        Sleep(12)),
                Sleep
        )
    }


    fun attacking(): Node = fallback(
            DispatchParallel("Attacking", { Cluster.mobileCombatUnits }) { myCluster ->
                val allies: Position? = null
                val board = AttackersBoard()
                fallback(
                        sequence(
                                Inline("Find me some enemies") {
                                    board.enemies = Cluster.enemyClusters.minBy {
                                        val distance = MyInfo.myBases.map { base -> base as PlayerUnit; base.getDistance(it.position) }.min()
                                                ?: Double.MAX_VALUE
                                        val eval = CombatEval.probabilityToWin(myCluster.units.map { SimUnit.of(it) }, it.units.filter { it is Attacker && it.isCompleted }.map { SimUnit.of(it) })
                                        it.position.getDistance(myCluster.position) - 500 * eval +
                                                min(distance / 5.0, 300.0)

                                    }?.units?.toList() ?: return@Inline NodeStatus.RUNNING
                                    NodeStatus.SUCCEEDED
                                },
                                Condition("Good combat eval?") {
                                    val combatEval = ClusterCombatInfo.getInfo(myCluster).attackEval
                                    combatEval > 0.6 ||
                                            combatEval > 0.40 && myCluster.units.any { me -> board.enemies.any { it.canAttack(me) } }
                                },
                                Inline("Who should attack?") {
                                    val units = myCluster.units.toMutableList()
                                    units.retainAll(Board.resources.units)
                                    board.attackers = units
                                    Board.resources.reserveUnits(units)
                                    NodeStatus.SUCCEEDED
                                },
                                attack(board)
                        ),
                        DispatchParallel("Flee", { myCluster.units.filter { Board.resources.units.contains(it) } }) {
                            sequence(ReserveUnit(it), flee(it))
                        },
                        joinUp(allies, myCluster),
                        Sleep)
            },
            Sleep
    )

    private fun joinUp(allies: Position?, myCluster: Cluster<MobileUnit>): Sequence {
        var allies1 = allies
        return sequence(
                Inline("Find me some allies") {
                    allies1 = Cluster.mobileCombatUnits.filter {
                        it != myCluster &&
                                FTTBot.bwem.data.getMiniTile(it.position.toWalkPosition()).isWalkable
                    }.minBy { it.position.getDistance(myCluster.position) }?.position
                            ?: if (!MyInfo.myBases.isEmpty()) (MyInfo.myBases[0] as PlayerUnit).position else return@Inline NodeStatus.RUNNING
                    NodeStatus.SUCCEEDED
                },
                Delegate {
                    val available = myCluster.units.filter { Board.resources.units.contains(it) }
                    Board.resources.reserveUnits(available)
                    reach(available, allies1!!)
                }
        )
    }

    fun shouldIncreaseDefense(at: Position): Node {
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