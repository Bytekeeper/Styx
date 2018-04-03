package org.fttbot.task

import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MSequence.Companion.msequence
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.EnemyInfo
import org.fttbot.info.MyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.info.canAttack
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.Burrowable
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Unit

object Actions {
    fun hasReached(unit: MobileUnit, position: Position, tolerance: Int = 64) =
            unit.getDistance(position) <= tolerance

    fun canReachSafely(unit: MobileUnit, position: Position): Boolean {
        val dangerAreas = EnemyInfo.occupiedAreas.keys - MyInfo.occupiedAreas.keys
        return FTTBot.bwem.getPath(unit.position, position)
                .none { dangerAreas.contains(it.areas.first) || dangerAreas.contains(it.areas.second) }
    }

    fun reachSafely(unit: MobileUnit, position: Position, tolerance: Int): Node<Any, Any> {
        return sequence(
                Condition("targetPosition $position safe?") { canReachSafely(unit, position) },
                Delegate { reach(unit, position, tolerance) }
        )
    }

    fun reach(unit: MobileUnit, position: Position, tolerance: Int): Node<Any, Any> {
        // TODO: "Search" for a way
        return MaxTries("$unit -> $position", 24 * 60,
                fallback(
                        Condition("Reached $position with $unit") { hasReached(unit, position, tolerance) },
                        msequence("Order $unit to $position",
                                makeMobile(unit),
                                MoveCommand(unit, position),
                                CheckIsClosingIn(unit, position)
                        )
                ))
    }

    fun reach(unit: List<MobileUnit>, position: Position, tolerance: Int = 128) : Node<Any, Any> =
            DispatchParallel({unit}) {
                reach(it, position, tolerance)
            }

    private fun CheckIsClosingIn(unit: MobileUnit, position: Position): Repeat<Any> {
        return Repeat(child = sequence(
                Delegate {
                    val distance = unit.getDistance(position)
                    val frame = FTTBot.frameCount
                    Inline<Any>("Closing in?") {
                        val deltaFrames = FTTBot.frameCount - frame
                        if (deltaFrames > 10 * 24 || deltaFrames > 5 && !unit.isMoving)
                            NodeStatus.FAILED
                        else if (unit.getDistance(position) < distance)
                            NodeStatus.SUCCEEDED
                        else
                            NodeStatus.RUNNING
                    }
                })
        )
    }

    private fun makeMobile(unit: MobileUnit): Fallback<Any, Any> {
        return fallback(
                Condition("$unit not burrowed") { unit !is Burrowable || !unit.isBurrowed },
                Delegate { UnburrowCommand(unit) }
        )
    }

    fun flee(s: MobileUnit): Sequence<Any, Any> {
        return sequence(
                Condition("Any close enemy") {
                    UnitQuery.enemyUnits.any { it.canAttack(s, 150) }
                },
                Condition("Has base") { !MyInfo.myBases.isEmpty() },
                Delegate { reach(s, (MyInfo.myBases[0] as Unit).position, 300) }
        )
    }
}