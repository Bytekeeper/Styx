package org.styx.actions

import bwapi.Position
import bwapi.Unit
import org.styx.Context.command
import org.styx.FlowStatus

object Move {
    fun move(unit: Unit, to: Position, threshold: Int = 8): FlowStatus {
        require(unit.type.canMove()) { "${unit.type} can't move!" }
        if (unit.getDistance(to) <= threshold) return FlowStatus.DONE
        command.move(unit, to)
        return FlowStatus.RUNNING
    }
}