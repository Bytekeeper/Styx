package org.styx.actions

import bwapi.Position
import bwapi.TilePosition
import bwapi.Unit
import bwapi.UnitType
import org.styx.Context.command
import org.styx.FlowStatus
import org.styx.actions.Move.move
import org.styx.plus

object Build {
    fun build(worker: Unit, type: UnitType, at: TilePosition): FlowStatus =
            move(worker, at.toPosition() + Position(type.width() / 2, type.height() / 2), 5 * 32)
                    .then {
                        command.build(worker, at, type)
                        FlowStatus.RUNNING
                    }
}