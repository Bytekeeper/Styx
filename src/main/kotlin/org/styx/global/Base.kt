package org.styx.global

import bwapi.Position
import bwapi.TilePosition
import org.styx.SUnit

class Base(val centerTile: TilePosition,
           val center: Position,
           val isStartingLocation: Boolean,
           var mainResourceDepot: SUnit? = null,
           var lastSeenFrame: Int? = null,
           var hasGas: Boolean,
           var populated: Boolean = false)