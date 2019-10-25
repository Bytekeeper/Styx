package org.styx

import bwapi.Position
import bwapi.UnitType

data class PendingUnit(
        val trainer: SUnit,
        val position: Position,
        val unitType: UnitType,
        val remainingFrames: Int)