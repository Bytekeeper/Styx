package org.fttbot.task.move

import org.fttbot.*
import org.fttbot.FTTBot.geometryFactory
import org.fttbot.info.MyInfo
import org.fttbot.info.potentialAttackers
import org.fttbot.task.Action
import org.fttbot.task.TaskStatus
import org.openbw.bwapi4j.unit.MobileUnit

class AvoidCombat(private val unit: MobileUnit, utility: Double = 1.0) : Action(utility) {
    override fun processInternal(): TaskStatus {
        val attackerCoords = unit.potentialAttackers(192).map { it.position.toCoordinate() }.toTypedArray()
        if (attackerCoords.isEmpty()) {
            return TaskStatus.DONE
        }
        MyInfo.unitStatus[unit] = "Retreat/Safe"

        val escapeBoundary = geometryFactory.createMultiPointFromCoords(attackerCoords).buffer(400.0, 3)
                .coordinates.asSequence()
                .map { it.toPosition() }
                .filter { it.isValidPosition }
        val reallySafePlace = Potential.reallySafePlace() ?: unit.position
        val escapePosition =
                if (unit.isFlying) {
                    escapeBoundary
                            .minBy {
                                (if (it.toWalkPosition().area != unit.position.toWalkPosition().area) -300 else 0) +
                                        it.getDistance(unit.position) * 3 + it.getDistance(reallySafePlace)
                            }
                } else {
                    escapeBoundary
                            .filter { it.toWalkPosition().isWalkable }
                            .minBy {
                                path(unit.position, it).length * 2 + it.getDistance(reallySafePlace)
                            }
                } ?: reallySafePlace
        // TODO check if we're crossing enemies here and FAIL instead (could happen for ground units)
        if (unit.targetPosition.getDistance(escapePosition) > 32) {
            Commands.move(unit, escapePosition)
        }
        return TaskStatus.RUNNING
    }

}