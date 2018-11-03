package org.fttbot.task.move

import org.fttbot.*
import org.fttbot.info.MyInfo
import org.fttbot.task.Action
import org.fttbot.task.TaskStatus
import org.openbw.bwapi4j.unit.MobileUnit

class KeepChokesClear(private val unit: MobileUnit) : Action() {
    override fun processInternal(): TaskStatus {
        if (unit.isFlying) return TaskStatus.DONE
        val position = unit.position
        val walkPosition = position.toWalkPosition()
        FTTBot.bwem.getArea(walkPosition)
                ?.let { area ->
                    if (area.chokePoints.any { it.center.toPosition().getDistance(position) < 96 }) {
                        MyInfo.unitStatus[unit] = "Shove it!"
                        if (unit.targetPosition.getDistance(position) < 64) {
                            FTTBot.geometryFactory.createPoint(position.toCoordinate())
                                    .buffer(128.0)
                                    .coordinates.asSequence()
                                    .map { it.toPosition() }
                                    .filter { it.isValidPosition && it.toWalkPosition().isWalkable }
                                    .firstOrNull()?.let { Commands.move(unit, it) }
                                    ?: return TaskStatus.RUNNING
                        }
                    }
                }
        return TaskStatus.DONE
    }

}