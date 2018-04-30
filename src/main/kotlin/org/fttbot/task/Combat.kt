package org.fttbot.task

import bwapi4j.org.apache.commons.lang3.mutable.MutableInt
import com.badlogic.gdx.math.RandomXS128
import com.badlogic.gdx.math.Vector2
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
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import kotlin.math.max
import kotlin.math.min

data class AttackersBoard(var attackers: List<PlayerUnit> = emptyList(),
                          var reluctant: List<PlayerUnit> = emptyList(),
                          var enemies: List<PlayerUnit> = emptyList(),
                          var eval: Double = 0.5)

data class AttackBoard(val unit: MobileUnit, var target: PlayerUnit? = null, var betterPosition: Position? = null)
data class CombatInfo(val myUnits: List<PlayerUnit>, val enemy: Cluster<PlayerUnit>, val eval: Double)

object Combat {
    private val tv = Vector2()
    private val rnd = RandomXS128()
    fun attack(board: AttackersBoard): Node {
        var avgHealth = 0.0
        return sequence(
                Inline("Average health?") {
                    avgHealth = board.attackers.map { (it.hitPoints + it.shields) / (it.maxHitPoints() + it.maxShields()).toDouble() }.average()
                    NodeStatus.SUCCEEDED
                },
                DispatchParallel("Attack", { board.attackers.filterIsInstance(MobileUnit::class.java) }) { unit ->
                    val childBoard = AttackBoard(unit)
                    var lastPositionCheck = 0
                    fallback(
                            sequence(
                                    Inline("Determine best target") {
                                        childBoard.target = findBestTarget(board.enemies, unit)
                                        if (childBoard.target !is Dragoon && childBoard.target !is Probe && unit is Mutalisk && UnitQuery.enemyUnits.inRadius(unit, 100).any { it is Dragoon }) {
//                                            println("Should attack me!!!")
                                        }
                                        if (childBoard.target == null)
                                            NodeStatus.FAILED
                                        else
                                            NodeStatus.SUCCEEDED
                                    },
                                    Inline("Determine potentially better position") {
                                        if (!unit.isFlying || FTTBot.frameCount - lastPositionCheck < 24 || !unit.canAttack(childBoard.target!!))
                                            return@Inline NodeStatus.SUCCEEDED
                                        lastPositionCheck = FTTBot.frameCount
                                        val weaponRange = (unit as Attacker).getWeaponAgainst(childBoard.target!!).maxRange()
                                        val threats = board.enemies.filter { it.canAttack(unit, 64) } - childBoard.target!!
                                        var bestThreats = if (childBoard.betterPosition == null) threats.size else
                                            threats.count { it.getDistance(childBoard.betterPosition) - 64 <= (it as Attacker).getWeaponAgainst(unit).maxRange() }
                                        if (bestThreats == 0)
                                            return@Inline NodeStatus.SUCCEEDED
                                        var result: Position? = childBoard.betterPosition
                                        for (i in 1..20) {
                                            val pos = tv.setToRandomDirection().scl(rnd.nextFloat() * weaponRange).add(childBoard.target!!.position.toVector()).toPosition()
                                            if (FTTBot.game.bwMap.isValidPosition(pos) && unit.getDistance(pos) < 192) {
                                                val remainingThreats = threats.count { it.getDistance(pos) - 64 <= (it as Attacker).getWeaponAgainst(unit).maxRange() }
                                                if (remainingThreats < bestThreats || result != null && remainingThreats == bestThreats && unit.getDistance(pos) < unit.getDistance(result)) {
                                                    result = pos
                                                    bestThreats = remainingThreats
                                                }
                                            }
                                        }
                                        childBoard.betterPosition = result
                                        NodeStatus.SUCCEEDED
                                    },
                                    sequence(
                                            avoidDamageBelowHealth(unit, avgHealth),
                                            fallback(
                                                    Condition("Good position?") {
                                                        childBoard.betterPosition == null || unit.getDistance(childBoard.betterPosition!!) < 16
                                                    },
                                                    Delegate({ childBoard.betterPosition!!.getDistance(unit.targetPosition) > 8 }) { reach(unit, childBoard.betterPosition!!, 8) }
                                            ),
                                            attack(unit, childBoard)
                                    )
                            ),
                            flee(unit),
                            Sleep
                    )
                }, Sleep)
    }

    private fun avoidDamageBelowHealth(unit: MobileUnit, hpPercent: Double): Node {
        return fallback(
                Condition("Health good, not under attack or just not fit for running?") {
                    val myHealth = (unit.hitPoints + unit.shields) / (unit.maxHitPoints() + unit.maxShields()).toDouble()
                    !unit.isUnderAttack || myHealth >= hpPercent / 3 ||
                            (unit is SiegeTank && unit.isSieged) ||
                            (unit is Lurker && unit.isBurrowed)
                },
                flee(unit)
        )
    }

    private fun findBestTarget(targets: List<PlayerUnit>, unit: MobileUnit): PlayerUnit? {
        val simUnit = SimUnit.of(unit)
        return targets.filter {
            unit as Attacker
            it.isDetected && unit.getWeaponAgainst(it).type() != WeaponType.None
        }.minBy {
            val enemySim = SimUnit.of(it)
            val simResult = attackScore(simUnit, enemySim)
            simResult
        }
    }

    fun attackScore(attackerSim: SimUnit, enemySim: SimUnit): Double {
        return (enemySim.hitPoints + enemySim.shield) / max(attackerSim.damagePerFrameTo(enemySim), 0.01) +
                (if (enemySim.type == UnitType.Zerg_Larva || enemySim.type == UnitType.Zerg_Egg) 8000 else 0) +
                (if (enemySim.type.isWorker) -300 else 0) +
                3 * (enemySim.position?.getDistance(attackerSim.position) ?: 0) +
                (if (enemySim.canAttack(attackerSim, 64))
                    -enemySim.damagePerFrameTo(attackerSim)
                else
                    0.0) * 1000 +
                (if (enemySim.groundWeapon.type() != WeaponType.None || enemySim.airWeapon.type() != WeaponType.None || enemySim.type == UnitType.Terran_Bunker) -200.0 else 0.0) +
                (if (enemySim.detected) 0 else 500) +
                (if (enemySim.type.isAddon) 8000 else 0) +
                (if (attackerSim.canAttack(enemySim)) {
                    if (enemySim.type == UnitType.Zerg_Lurker && enemySim.isHidden)
                        -2000
                    else
                        -300
                } else 0) +
                (enemySim.topSpeed - attackerSim.topSpeed) * 30
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
                            val target = board.target!!
                            val dst = min(128.0, target.getDistance(unit) / unit.topSpeed).toInt()
                            reachBoard.position = EnemyInfo.predictedPositionOf(target, dst)
                            if (!FTTBot.game.bwMap.isValidPosition(reachBoard.position)) {
                                reachBoard.position = target.position
                            }
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
                    val targetPosition = unit.position.minus(storm.position).toVector().setLength(92f).add(unit.position.toVector()).toPosition()
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
                                        val distanceToBase = MyInfo.myBases.map { base -> base as PlayerUnit; base.getDistance(enemyPosition) }.min()
                                                ?: Double.MAX_VALUE
                                        val distanceToAttackers = if (board.attackers.any { !it.isFlying }) {
                                            val enemyDistance = MutableInt()
                                            FTTBot.bwem.getPath(myCluster.position, enemyPosition, enemyDistance)
                                            enemyDistance.toInteger()
                                        } else
                                            enemyPosition.getDistance(myCluster.position)
                                        distanceToAttackers - 250 * it.eval + min(distanceToBase / 5.0, 400.0)
                                    }
                                    board.enemies = bestCombat?.enemy?.units
                                            ?: return@Inline NodeStatus.RUNNING
                                    board.reluctant = board.attackers - bestCombat.myUnits
                                    board.attackers = bestCombat.myUnits
                                    board.eval = bestCombat.eval
                                    NodeStatus.SUCCEEDED
                                },
                                Condition("Good combat eval?") {
                                    board.eval > 0.6 ||
                                            board.eval > 0.40 && myCluster.units.any { me -> board.enemies.any { it.canAttack(me) } }
                                },
                                Inline("Who should attack?") {
                                    Board.resources.reserveUnits(board.attackers)
                                    NodeStatus.SUCCEEDED
                                },
                                parallel(2,
                                        attack(board),
                                        fallback(
                                                DispatchParallel("Let reluctants fall back", { board.reluctant.filterIsInstance(MobileUnit::class.java) }) {
                                                    sequence(flee(it), ReserveUnit(it))
                                                },
                                                Sleep
                                        )
                                )
                        ),
                        DispatchParallel("Flee", { (board.attackers + board.reluctant).filterIsInstance(MobileUnit::class.java) }) {
                            sequence(flee(it), ReserveUnit(it))
                        },
                        joinUp(myCluster),
                        Sleep)
            },
            Sleep
    )

    private fun joinUp(myCluster: Cluster<MobileUnit>): Sequence {
        val reachBoard = ReachBoard(tolerance = 64)
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
            val distanceFactor = myCluster!!.position.getDistance(enemyCluster!!.position) * 0.002
            combatEval < 0.65 - distanceFactor
        }
    }
}