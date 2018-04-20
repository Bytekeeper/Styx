package org.fttbot.task

import com.badlogic.gdx.math.Vector2
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MSequence.Companion.msequence
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
            unit.getDistance(position) <= tolerance

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
        var nextWaypoint: Position? = null
        return MaxTries("$unit -> ${board.position}", 12 * 60,
                fallback(
                        Condition("Reached ${board.position} with $unit") { hasReached(unit, board.position!!, board.tolerance) },
                        Repeat(child = msequence("Move $unit to ${board.position}",
                                makeMobile(unit),
                                Inline("Next waypoint") {
                                    if (unit.getDistance(board.position) < 200 || unit.isFlying) {
                                        nextWaypoint = board.position
                                    } else {
                                        nextWaypoint = FTTBot.bwem.getPath(unit.position, board.position).firstOrNull { it.center.toPosition().getDistance(unit.position) >= 200 }?.center?.toPosition()
                                                ?: board.position
                                    }
                                    NodeStatus.SUCCEEDED
                                },
                                Delegate { MoveCommand(unit, nextWaypoint!!) },
                                Delegate { CheckIsClosingIn(unit, nextWaypoint!!) }
                        ))
                ))
    }

    fun reach(unit: List<MobileUnit>, reachBoard: ReachBoard): Node =
            DispatchParallel("Reach", { unit }) {
                reach(it, reachBoard)
            }

    fun reach(unit: List<MobileUnit>, position: Position, tolerance: Int = 128): Node =
            DispatchParallel("Reach", { unit }) {
                reach(it, position, tolerance)
            }

    private fun CheckIsClosingIn(unit: MobileUnit, position: Position): BaseNode {
        return Delegate {
            var distance = unit.getDistance(position)
            var frame = FTTBot.frameCount
            Inline("Closing in?") {
                val deltaFrames = FTTBot.frameCount - frame
                val currentDistance = unit.getDistance(position)
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

    private fun makeMobile(unit: MobileUnit): Fallback {
        return fallback(
                Condition("$unit not burrowed") { unit !is Burrowable || !unit.isBurrowed },
                fallback(
                        Condition("$unit is unborrowing?") { unit.order == org.openbw.bwapi4j.type.Order.Unburrowing },
                        Delegate { UnburrowCommand(unit) }
                )
        )
    }

    fun flee(unit: MobileUnit): Sequence {
        val reachBoard = ReachBoard(tolerance = 8)
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
                    force.setZero()
                    Potential.addThreatRepulsion(force, unit)
                    if (force.isZero)
                        return@Inline NodeStatus.FAILED
                    if (!unit.isFlying) {
                        Potential.addWallRepulsion(force, unit, 2.3f)
                        Potential.addSafeAreaAttraction(force, unit, 0.7f)
                    } else {
                        Potential.addWallAttraction(force, unit, 1.5f)
                        Potential.addSafeAreaAttractionDirect(force, unit, 1.3f)
                    }
                    force.nor()
                    reachBoard.position = unit.position + force.scl(64f).toPosition()
                    NodeStatus.SUCCEEDED
                },
                Delegate { reach(unit, reachBoard) }
        )
    }
}