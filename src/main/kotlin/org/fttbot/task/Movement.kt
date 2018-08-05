package org.fttbot.task

import org.fttbot.FTTBot
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.MobileUnit

class Move(val unit: MobileUnit, var to: Position, val tolerance: Int = 32) : Task() {
    override val utility: Double = 0.0

    override fun toString(): String = "Moving $unit to $to"

    override fun processInternal() : TaskStatus {
        if (to.getDistance(unit.position) <= tolerance) return TaskStatus.DONE
        val targetPosition =
                if (unit.isFlying)
                    to
                else
                    FTTBot.bwem.getPath(unit.position, to)
                            .firstOrNull { it.center.toPosition().getDistance(to) >= 160 }?.center?.toPosition()
                            ?: to

        if (unit.targetPosition != targetPosition) {
            unit.move(to)
        }
        return TaskStatus.RUNNING
    }
}
