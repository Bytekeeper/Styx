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

data class AttackersBoard(var attackers: List<PlayerUnit> = emptyList(),
                          var reluctant: List<PlayerUnit> = emptyList(),
                          var enemies: List<PlayerUnit> = emptyList(),
                          var eval: Double = 0.5)

data class AttackBoard(val unit: MobileUnit, var target: PlayerUnit? = null)
data class CombatInfo(val myUnits: List<PlayerUnit>, val enemy: Cluster<PlayerUnit>, val eval: Double)

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
                    (if (it is Larva || it is Egg) 8000 else 0) +
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
                        if (unit is Lurker && (unit.isBurrowed || unit.order == Order.Burrowing)) -1000 else -300
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
                val board = AttackersBoard()
                fallback(
                        sequence(
                                Inline("Who is ready to attack?") {
                                    val units = myCluster.units.toMutableList()
                                    units.retainAll(Board.resources.units)
                                    board.attackers = units
                                    NodeStatus.SUCCEEDED
                                },
                                Inline("Find me some enemies") {
                                    val combatInfos = Cluster.enemyClusters.map {
                                        val relevantEnemies = it.units.filter { it is Attacker && it.isCompleted }
                                        val eval = CombatEval.bestProbilityToWin(board.attackers.map { SimUnit.of(it) }, relevantEnemies.map { SimUnit.of(it) })
                                        CombatInfo(board.attackers.filter { eval.first.map { it.id }.contains(it.id) }, it, eval.second)
                                    }
                                    val bestCombat = combatInfos.minBy {
                                        val enemyPosition = it.enemy.position
                                        val distance = MyInfo.myBases.map { base -> base as PlayerUnit; base.getDistance(enemyPosition) }.min()
                                                ?: Double.MAX_VALUE
                                        enemyPosition.getDistance(myCluster.position) - 600 * it.eval + min(distance / 5.0, 400.0)
                                    }
                                    board.enemies = bestCombat?.enemy?.units ?: return@Inline NodeStatus.RUNNING
                                    board.reluctant = board.attackers - bestCombat.myUnits
                                    board.attackers = bestCombat.myUnits
                                    board.eval = bestCombat.eval
                                    NodeStatus.SUCCEEDED
                                },
                                Condition("Good combat eval?") {
                                    val clusterCombatInfo = ClusterCombatInfo(board.attackers, myCluster.position)
                                    val combatEval = clusterCombatInfo.attackEval
                                    combatEval > 0.6 ||
                                            combatEval > 0.40 && myCluster.units.any { me -> clusterCombatInfo.enemyUnits.any { it.canAttack(me) } }
                                },
                                Inline("Who should attack?") {
                                    Board.resources.reserveUnits(board.attackers)
                                    NodeStatus.SUCCEEDED
                                },
                                parallel(2,
                                        attack(board),
                                        fallback(
                                                DispatchParallel("Let reluctants fall back", { board.reluctant.filterIsInstance(MobileUnit::class.java) }) {
                                                    sequence(ReserveUnit(it), fallback(flee(it), sequence(ReleaseUnit(it), Fail)))
                                                },
                                                Sleep
                                        )
                                )
                        ),
                        DispatchParallel("Flee", { (board.attackers + board.reluctant).filterIsInstance(MobileUnit::class.java) }) {
                            sequence(ReserveUnit(it), fallback(flee(it), sequence(ReleaseUnit(it), Fail)))
                        },
                        joinUp(myCluster),
                        Sleep)
            },
            Sleep
    )

    private fun joinUp(myCluster: Cluster<MobileUnit>): Sequence {
        val reachBoard = ReachBoard(tolerance = 32)
        return sequence(
                Inline("Find me some allies") {
                    reachBoard.position = Cluster.mobileCombatUnits.filter {
                        it != myCluster &&
                                FTTBot.bwem.data.getMiniTile(it.position.toWalkPosition()).isWalkable
                    }.minBy { it.position.getDistance(myCluster.position) }?.position
                            ?: if (!MyInfo.myBases.isEmpty()) (MyInfo.myBases[0] as PlayerUnit).position else return@Inline NodeStatus.RUNNING
                    NodeStatus.SUCCEEDED
                },
                Delegate {
                    val available = myCluster.units.filter { Board.resources.units.contains(it) }
                    Board.resources.reserveUnits(available)
                    reach(available, reachBoard)
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