package org.fttbot.task

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.EnemyInfo
import org.fttbot.info.MyInfo
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.unit.Burrowable
import org.openbw.bwapi4j.unit.MobileUnit


data class ReachBoard(var position: Position? = null, var tolerance: Int)
data class FleeBoard(var position: Position? = null, var sinceFrame: Int = 0)

object Actions {
    fun hasReached(unit: MobileUnit, position: Position, tolerance: Int = 64) =
            unit.position.getDistance(position) <= tolerance

    fun canReachSafely(unit: MobileUnit, position: Position): Boolean {
        val dangerAreas = EnemyInfo.occupiedAreas.keys - MyInfo.occupiedAreas.keys
        return FTTBot.bwem.getPath(unit.position, position)
                .none { dangerAreas.contains(it.areas.first) || dangerAreas.contains(it.areas.second) }
    }

    fun reachSafely(unit: MobileUnit, position: Position, tolerance: Int): Node {
        return fallback(flee(unit),
                sequence(
                        Condition("targetPosition $position safe?") { canReachSafely(unit, position) },
                        Delegate { reach(unit, position, tolerance) }
                ))
    }

    fun reach(unit: MobileUnit, position: Position, tolerance: Int): Node =
            reach(unit, ReachBoard(position, tolerance))

    fun reach(unit: MobileUnit, board: ReachBoard): Node {
        // TODO: "Search" for a way
        return sequence(
                Inline("Make valid") {
                    if (!FTTBot.game.bwMap.isValidPosition(board.position)) {
                        board.position = Position(MathUtils.clamp(board.position!!.x, 0, FTTBot.game.bwMap.mapWidth() * 32),
                                MathUtils.clamp(board.position!!.y, 0, FTTBot.game.bwMap.mapHeight() * 32))
                    }
                    NodeStatus.SUCCEEDED
                },
                fallback(
                        Condition("Reached ${board.position} with $unit") { hasReached(unit, board.position!!, board.tolerance) },
                        sequence(Condition("Moving?") { unit.isMoving && unit.targetPosition.getDistance(board.position) < 16 }, Sleep),
                        sequence(ensureCanMove(unit), Delegate { MoveCommand(unit, board.position!!) }, Sleep),
                        Sleep
                )
        )
//        var nextWaypoint: Position? = null
//        return MaxTries("$unit -> ${board.position}", 12 * 60,
//                fallback(
//                        Condition("Reached ${board.position} with $unit") { hasReached(unit, board.position!!, board.tolerance) },
//                        Inline("Make valid") {
//                            if (!FTTBot.game.bwMap.isValidPosition(board.position)) {
//                                board.position = Position(MathUtils.clamp(board.position!!.x, 0, FTTBot.game.bwMap.mapWidth() * 32),
//                                        MathUtils.clamp(board.position!!.y, 0, FTTBot.game.bwMap.mapHeight() * 32))
//                            }
//                            NodeStatus.FAILED
//                        },
//                        Repeat(child = msequence("Move $unit to ${board.position}",
//                                ensureCanMove(unit),
//                                Inline("Next waypoint") {
//                                    if (unit.position.getDistance(board.position) < 200 || unit.isFlying) {
//                                        nextWaypoint = board.position
//                                    } else {
//                                        nextWaypoint = FTTBot.bwem.getPath(unit.position, board.position).firstOrNull { it.center.toPosition().getDistance(unit.position) >= 200 }?.center?.toPosition()
//                                                ?: board.position
//                                    }
//                                    NodeStatus.SUCCEEDED
//                                },
//                                fallback(
//                                        Condition("Correct targetpos?") { nextWaypoint!!.getDistance(unit.targetPosition) <= board.tolerance },
//                                        sequence(Delegate { MoveCommand(unit, nextWaypoint!!) })
//                                ),
//                                Delegate { CheckIsClosingIn(unit, nextWaypoint!!) }
//                        ))
//                ))
    }

    fun reach(unit: List<MobileUnit>, reachBoard: ReachBoard): Node =
            DispatchParallel("Reach", { unit }) {
                reach(it, reachBoard)
            }

    private fun CheckIsClosingIn(unit: MobileUnit, position: Position): BaseNode {
        return Delegate {
            var distance = unit.position.getDistance(position)
            var frame = FTTBot.frameCount
            Inline("Closing in?") {
                val deltaFrames = FTTBot.frameCount - frame
                val currentDistance = unit.position.getDistance(position)
                if (deltaFrames > 10 * 24 || deltaFrames > 5 && !unit.isMoving)
                    NodeStatus.FAILED
                else
                    if (currentDistance <= TilePosition.SIZE_IN_PIXELS) {
                        NodeStatus.SUCCEEDED
                    } else {
                        if (currentDistance < distance) {
                            frame = FTTBot.frameCount
                            distance = currentDistance
                        }
                        NodeStatus.RUNNING
                    }
            }
        }
    }

    private fun ensureCanMove(unit: MobileUnit): Fallback {
        return fallback(
                Condition("$unit not burrowed") { unit !is Burrowable || !unit.isBurrowed },
                fallback(
                        Condition("$unit is unborrowing?") { unit.order == org.openbw.bwapi4j.type.Order.Unburrowing },
                        sequence(Delegate { UnburrowCommand(unit) }, Sleep),
                        Sleep
                )
        )
    }

    fun flee(unit: MobileUnit, avoidRange: Int? = null): Sequence {
        val reachBoard = ReachBoard(tolerance = 4)
        val fleeBoard = FleeBoard()
        return sequence(
                Inline("Stuck?") {
                    if (fleeBoard.position?.getDistance(unit.position) ?: Int.MAX_VALUE < 32) {
                        if (FTTBot.frameCount - fleeBoard.sinceFrame > 24) {
                            NodeStatus.FAILED
                        } else
                            NodeStatus.SUCCEEDED
                    } else {
                        fleeBoard.position = unit.position
                        fleeBoard.sinceFrame = FTTBot.frameCount
                        NodeStatus.SUCCEEDED
                    }
                },
                Inline("Threat Vector") {
                    val force = Vector2()
                    Potential.addThreatRepulsion(force, unit, avoidRange)
                    if (force.isZero)
                        return@Inline NodeStatus.FAILED
                    if (!unit.isFlying) {
                        Potential.addWallRepulsion(force, unit, 1.2f)
                        Potential.addSafeAreaAttraction(force, unit, 1.3f)
                        Potential.addCollisionRepulsion(force, unit, 0.8f)
                    } else {
                        Potential.addWallAttraction(force, unit, 0.5f)
                        Potential.addSafeAreaAttractionDirect(force, unit, 1.3f)
                    }
                    force.nor()
                    val pos = unit.position + force.scl(unit.topSpeed.toFloat() * 20).toPosition()
                    reachBoard.position = Position(MathUtils.clamp(pos.x, 0, FTTBot.game.bwMap.mapWidth() * 32),
                            MathUtils.clamp(pos.y, 0, FTTBot.game.bwMap.mapHeight() * 32))
                    NodeStatus.SUCCEEDED
                },
                Inline("Move move move") {
                    unit.move(reachBoard.position)
                    NodeStatus.SUCCEEDED
                }
        )
    }
}