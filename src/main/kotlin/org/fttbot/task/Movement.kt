package org.fttbot.task

import org.fttbot.FTTBot
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.MobileUnit

class Move(val unit: MobileUnit, var to: Position, val tolerance: Int = 32) : Action() {
    override fun toString(): String = "Moving $unit to $to"

    override fun processInternal() : TaskStatus {
        if (to.getDistance(unit.position) <= tolerance) {
            if (unit.targetPosition.getDistance(to) > tolerance) {
                unit.move(to)
            }
            return TaskStatus.DONE
        }
        val targetPosition =
                if (unit.isFlying)
                    to
                else
                    FTTBot.bwem.getPath(unit.position, to)
                            .firstOrNull { it.center.toPosition().getDistance(unit.position) >= 160 }?.center?.toPosition()
                            ?: to

        if (unit.targetPosition.getDistance(targetPosition) > 64) {
            unit.move(targetPosition)
        }
        return TaskStatus.RUNNING
    }
}
