package org.fttbot.task

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
import org.openbw.bwapi4j.org.apache.commons.lang3.mutable.MutableInt
import org.openbw.bwapi4j.type.Order
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import kotlin.math.max
import kotlin.math.min

data class CombatBoard(var myUnits: List<PlayerUnit> = emptyList(),
                       var reluctant: List<PlayerUnit> = emptyList(),
                       var enemies: List<PlayerUnit> = emptyList(),
                       var eval: Double = 0.5)

data class AttackBoard(val unit: MobileUnit, var target: PlayerUnit? = null, var betterPosition: Position? = null)
data class CombatInfo(val myUnits: List<PlayerUnit>, val enemy: Cluster<PlayerUnit>, val eval: Double)

object Combat {
    private val tv = Vector2()
    private val rnd = RandomXS128()
    fun attack(board: CombatBoard): Node {
        var avgHealth = 0.0
        return sequence(
                Inline("Average health?") {
                    avgHealth = board.myUnits.map { (it.hitPoints + it.shields) / (it.maxHitPoints() + it.maxShields()).toDouble() }.average()
                    NodeStatus.SUCCEEDED
                },
                parallel(2,
                        DispatchParallel("Attack", { board.myUnits.filterIsInstance(MobileUnit::class.java) }) { unit ->
                            val childBoard = AttackBoard(unit)
                            var lastPositionCheck = 0
                            fallback(
                                    sequence(
                                            Inline("Determine best target") {
                                                childBoard.target = findBestTarget(board.enemies, unit)
                                                if (childBoard.target == null)
                                                    NodeStatus.FAILED
                                                else
                                                    NodeStatus.SUCCEEDED
                                            },
                                            Inline("Determine potentially better position") {
                                                if (FTTBot.frameCount - lastPositionCheck < 4 * 24 ||
                                                        unit is Lurker ||
                                                        unit is SiegeTank ||
                                                        (unit is Attacker && !unit.canMoveWithoutBreakingAttack) ||
                                                        !unit.canAttack(childBoard.target!!) ||
                                                        unit.isSuicideUnit)
                                                    return@Inline NodeStatus.SUCCEEDED
                                                lastPositionCheck = FTTBot.frameCount
                                                val simTarget = SimUnit.of(childBoard.target!!)
                                                val weaponRange = (unit as Attacker).maxRangeVs(childBoard.target!!)
                                                val relevantEnemies = (UnitQuery.enemyUnits.filter { it.canAttack(unit, 150) } - childBoard.target!!)
                                                        .map { SimUnit.of(it) }
                                                val simUnit = SimUnit.of(unit)
                                                simUnit.position = childBoard.betterPosition
                                                var currentThreats = if (childBoard.betterPosition == null) relevantEnemies.size else
                                                    relevantEnemies.count { it.canAttack(simUnit, 32) }
                                                if (currentThreats == 0)
                                                    return@Inline NodeStatus.SUCCEEDED
                                                var bestThreats = currentThreats
                                                var result: Position? = childBoard.betterPosition
                                                for (i in 1..15) {
                                                    val pos = tv.setToRandomDirection().scl(rnd.nextFloat() * (weaponRange + 32)).add(childBoard.target!!.position.toVector()).toPosition()
                                                    if (FTTBot.game.bwMap.isValidPosition(pos) &&
                                                            (unit.isFlying || FTTBot.game.bwMap.isWalkable(pos.toWalkPosition())) && unit.getDistance(pos) < 128) {
                                                        simUnit.position = pos
                                                        if (simUnit.canAttack(simTarget)) {
                                                            val remainingThreats = relevantEnemies.count { it.canAttack(simUnit, 32) }
                                                            if (remainingThreats < bestThreats || result != null && remainingThreats == bestThreats && unit.getDistance(pos) < unit.getDistance(result)) {
                                                                result = pos
                                                                bestThreats = remainingThreats
                                                            }
                                                        }
                                                    }
                                                }
                                                if (bestThreats < currentThreats / 2) {
                                                    childBoard.betterPosition = result
                                                }
                                                NodeStatus.SUCCEEDED
                                            },
                                            sequence(
                                                    avoidDamageBelowHealth(unit, avgHealth),
                                                    fallback(
                                                            Condition("Good position?") {
                                                                childBoard.betterPosition == null || unit.getDistance(childBoard.betterPosition!!) < (if (unit.isFlying) 8 else 32)
                                                            },
                                                            Delegate({ childBoard.betterPosition!!.getDistance(unit.targetPosition) > (if (unit.isFlying) 8 else 32) })
                                                            { reach(unit, childBoard.betterPosition!!, (if (unit.isFlying) 8 else 32)) }
                                                    ),
                                                    attack(unit, childBoard)
                                            )
                                    ),
                                    flee(unit),
                                    Sleep
                            )
                        },
                        DispatchParallel("Let reluctants fall back", { board.reluctant.filterIsInstance(MobileUnit::class.java) }) {
                            fallback(sequence(flee(it, 300), ReserveUnit(it)), Sleep)
                        }))
    }

    private fun avoidDamageBelowHealth(unit: MobileUnit, hpPercent: Double): Node {
        return fallback(
                Condition("Health good, not under attack or just not fit for running?") {
                    val myHealth = (unit.hitPoints + unit.shields) / (unit.maxHitPoints() + unit.maxShields()).toDouble()
                    !unit.isUnderAttack || myHealth >= hpPercent / 2 ||
                            (unit is SiegeTank && unit.isSieged) ||
                            (unit is Lurker && unit.isBurrowed) ||
                            (unit.isSuicideUnit)
                },
                flee(unit, 64)
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
        val wpn = attackerSim.determineWeaponAgainst(enemySim)
        if (wpn.type() == WeaponType.None) return -100000.0;
        val enemyWpn = enemySim.determineWeaponAgainst(attackerSim)
        val futurePosition = enemySim.position?.add(enemySim.velocity.scl(24f * 3).toPosition())
        val futureDistance = futurePosition?.minus(attackerSim.position ?: futurePosition)?.toVector()?.len() ?: 30f
        return (enemySim.hitPoints + enemySim.shield) / max(attackerSim.damagePerFrameTo(enemySim), 0.001) +
                (if (enemySim.type == UnitType.Zerg_Larva || enemySim.type == UnitType.Zerg_Egg || enemySim.type == UnitType.Protoss_Interceptor || enemySim.type.isAddon) 25000 else 0) +
                (if (enemySim.type.isWorker) -150 else 0) +
                (if (enemySim.type.spaceProvided() > 0) -200 else 0) +
                (if (enemySim.canHeal) -100 else 0) +
                1.0 * futureDistance +
                2.0 * (enemySim.position?.getDistance(attackerSim.position) ?: 0) +
                (if (enemyWpn.type() != WeaponType.None)
                    -enemySim.damagePerFrameTo(attackerSim)
                else
                    0.0) * (if (enemySim.hasSplashWeapon) 5000 else 3500) +
                (if (enemySim.type == UnitType.Protoss_Carrier) -300 else 0) +
                (if (enemySim.type == UnitType.Terran_Bunker) -200.0 else 0.0) +
                (if (enemySim.detected) 0 else 500) +
                (fastsig(max(0.0, futureDistance.toDouble() - wpn.maxRange() )) * 150 *
                        (if (attackerSim.type == UnitType.Zerg_Lurker && attackerSim.isBurrowed) 20.0 else 1.0)) +
                (enemySim.topSpeed - attackerSim.topSpeed) * 50
    }


    fun attack(unit: MobileUnit, board: AttackBoard): Node {
        val reachEnemyBoard = ReachBoard(tolerance = 100)
        return fallback(
                Condition("Target destroyed?") {
                    !board.target!!.exists() && !EnemyInfo.seenUnits.contains(board.target!!)
                },
                fleeFromStorm(unit),
                sequence(
                        fallback(Condition("Can I move safely?") { unit is Attacker && unit.canMoveWithoutBreakingAttack }, Sleep),
                        fallback(
                                Condition("Can I see you?") { board.target!!.isVisible },
                                sequence(
                                        Inline("Find enemies") {
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
        val reachBoard = ReachBoard(tolerance = 100)
        return fallback(
                Condition("Close enough?") {
                    val range = (unit as Attacker).maxRangeVs(board.target!!).toDouble() * (if (unit is Lurker && !unit.isBurrowed) 0.8 else 1.0)
                    val distance = unit.getDistance(board.target)
                    if (unit is Lurker) {
                        unit.isBurrowed && range * 1.4 >= distance || !unit.isBurrowed && range >= distance
                    } else {
                        range >= distance
                    }
                },
                sequence(
                        Inline("Update reach position to ${board.target?.position}") {
                            val target = board.target!!
                            val dst = min(128.0, target.getDistance(unit) / unit.topSpeed).toInt()
                            reachBoard.position = EnemyInfo.predictedPositionOf(target, max(24, dst))
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
                Delegate({ true }) {
                    val storm = FTTBot.game.bullets.minBy { unit.getDistance(it.position) } ?: return@Delegate Fail
                    val targetPosition = unit.position.minus(storm.position).toVector().setLength(92f).add(unit.position.toVector()).toPosition()
                    MoveCommand(unit, targetPosition)
                }
        )
    }

    fun defending(): Node = fallback(
            DispatchParallel("Defending", { MyInfo.myBases }) { base ->
                val board = CombatBoard()
                base as PlayerUnit
                parallel(Int.MAX_VALUE,
                        workerDefense(base),
                        fallback(
                                sequence(
                                        Condition("Attackers close to base?") {
                                            val enemyCluster = Cluster.enemyClusters.minBy { base.getDistance(it.position) }
                                                    ?: return@Condition false
                                            board.enemies = enemyCluster.units.filter { enemy ->
                                                UnitQuery.myBuildings.any { it.getDistance(base) < 300 && enemy.canAttack(it, 64) }
                                            }
                                            !board.enemies.isEmpty()
                                        },
                                        sequence(
                                                Inline("Who should attack?") {
                                                    val myUnits = Cluster.mobileCombatUnits.minBy { base.getDistance(it.position) }
                                                            ?: return@Inline NodeStatus.FAILED
                                                    val unitsAvailable = myUnits.units.filter { Board.resources.units.contains(it) }
                                                    board.myUnits = unitsAvailable
                                                    Board.resources.reserveUnits(unitsAvailable)
                                                    NodeStatus.SUCCEEDED
                                                },
                                                attack(board)
                                        )
                                ),
                                Sleep
                        )
                )
            }, Sleep
    )

    private fun workerDefense(base: PlayerUnit): Sequence {
        val workerDefenseBoard = WorkerDefenseBoard()
        val workerAttackersBoard = CombatBoard()
        return sequence(
                fallback(
                        WorkerDefense(base.position, workerDefenseBoard),
                        DispatchParallel("Flee you worker fools", { UnitQuery.myWorkers.inRadius(base.position, 300).filter { Board.resources.units.contains(it) } }) {
                            fallback(sequence(flee(it), Sleep), Sleep)
                        }
                ),
                sequence(
                        Inline("Assign workers to defend") {
                            workerAttackersBoard.myUnits = workerDefenseBoard.defendingWorkers
                            workerAttackersBoard.enemies = workerDefenseBoard.enemies
                            NodeStatus.SUCCEEDED
                        },
                        attack(workerAttackersBoard)),
                Sleep
        )
    }


    fun attacking(): Node = fallback(
            DispatchParallel("Attacking", { Cluster.mobileCombatUnits }) { myCluster ->
                val board = CombatBoard()
                fallback(
                        sequence(
                                Inline("Who is ready to attack?") {
                                    val units = myCluster.units.toMutableList()
                                    units.retainAll(Board.resources.units)
                                    board.myUnits = units
                                    NodeStatus.SUCCEEDED
                                },
                                Inline("Find me some enemies") {
                                    val combatInfos = Cluster.enemyClusters.map {
                                        val relevantEnemies = it.units.filter {
                                            it is Attacker && it.isCompleted
                                                    && (it !is Cloakable || it.isDetected)
                                                    && (it !is Burrowable || it.isDetected)
                                        }
                                        val eval = CombatEval.bestProbilityToWin(board.myUnits.map { SimUnit.of(it) }, relevantEnemies.map { SimUnit.of(it) })
                                        CombatInfo(board.myUnits.filter { eval.first.map { it.id }.contains(it.id) }, it, eval.second)
                                    }.filter { ci -> ci.myUnits.any { mine -> ci.enemy.units.any { mine is Attacker && mine.getWeaponAgainst(it).type() != WeaponType.None } } }
                                    val bestCombat = combatInfos.minBy {
                                        val enemyPosition = it.enemy.position
                                        val distanceToBase = MyInfo.myBases.map { base -> base as PlayerUnit; base.getDistance(enemyPosition) }.min()
                                                ?: Double.MAX_VALUE
                                        val distanceToAttackers = if (board.myUnits.any { !it.isFlying }) {
                                            try {
                                                val enemyDistance = MutableInt()
                                                FTTBot.bwem.getPath(myCluster.position, enemyPosition, enemyDistance)
                                                enemyDistance.toInteger()
                                            } catch (e: IllegalStateException) {
                                                Int.MAX_VALUE
                                            }
                                        } else
                                            enemyPosition.getDistance(myCluster.position)
                                        distanceToAttackers - 100 * it.eval + min(distanceToBase / 5.0, 400.0)
                                    }
                                    board.enemies = bestCombat?.enemy?.units
                                            ?: return@Inline NodeStatus.RUNNING
                                    board.reluctant = board.myUnits - bestCombat.myUnits
                                    board.myUnits = bestCombat.myUnits
                                    board.eval = bestCombat.eval
                                    if ((board.reluctant.all { it is Scourge } && board.reluctant.size > 2 || (board.eval < 0.6 && board.myUnits.any { it is Scourge })) && board.enemies.any { it.isFlying }) {
//                                        println("DOH!")
                                    }
                                    NodeStatus.SUCCEEDED
                                },
                                Condition("Good combat eval?") {
                                    board.eval > 0.65 ||
                                            board.eval > 0.52 && myCluster.units.any { me -> board.enemies.any { it.canAttack(me, 16) } }
                                },
                                Inline("Who should attack?") {
                                    Board.resources.reserveUnits(board.myUnits)
                                    NodeStatus.SUCCEEDED
                                },
                                attack(board)
                        ),
                        DispatchParallel("Flee", { (board.myUnits + board.reluctant).filterIsInstance(MobileUnit::class.java) }) {
                            fallback(sequence(flee(it), ReserveUnit(it)), findBetterCluster(myCluster, it))
                        },
                        Sleep)
            },
            Sleep
    )

    private fun findBetterCluster(myCluster: Cluster<MobileUnit>, unit: MobileUnit): Sequence {
        val reachBoard = ReachBoard(tolerance = 64)
        return sequence(
                Inline("Find me some allies") {
                    reachBoard.position = Cluster.mobileCombatUnits
                            .filter { myCluster != it && (unit.isFlying || FTTBot.bwem.data.getMiniTile(it.position.toWalkPosition()).isWalkable) }
                            .minBy {
                                it.position.getDistance(unit.position) -
                                        it.units.count { it.initialType == unit.initialType } * 200
                            }?.position
                            ?: if (!MyInfo.myBases.isEmpty()) (MyInfo.myBases[0] as PlayerUnit).position else return@Inline NodeStatus.RUNNING
                    NodeStatus.SUCCEEDED
                },
                sequence(
                        ReserveUnit(unit),
                        reach(unit, reachBoard)
                )
        )
    }
}