package org.styx

import bwapi.Position
import bwapi.Unit
import bwapi.UnitType
import com.badlogic.gdx.utils.Align.bottom
import com.badlogic.gdx.utils.Align.right
import org.styx.Context.map;
import kotlin.math.ceil

val Unit.stopFrames
    get() = when (type) {
        UnitType.Terran_Goliath, UnitType.Terran_Siege_Tank_Tank_Mode, UnitType.Terran_Siege_Tank_Siege_Mode,
        UnitType.Protoss_Reaver -> 1
        UnitType.Terran_SCV, UnitType.Zerg_Drone, UnitType.Protoss_Probe, UnitType.Terran_Wraith, UnitType.Terran_Battlecruiser,
        UnitType.Protoss_Scout, UnitType.Zerg_Lurker_Egg -> 2
        UnitType.Terran_Ghost, UnitType.Zerg_Hydralisk -> 3
        UnitType.Protoss_Arbiter, UnitType.Zerg_Zergling -> 4
        UnitType.Protoss_Zealot, UnitType.Protoss_Dragoon -> 7
        UnitType.Terran_Marine, UnitType.Terran_Firebat, UnitType.Protoss_Corsair -> 8
        UnitType.Protoss_Dark_Templar, UnitType.Zerg_Devourer -> 9
        UnitType.Zerg_Ultralisk -> 14
        UnitType.Protoss_Archon -> 15
        UnitType.Terran_Valkyrie -> 40
        else -> 2
    }


val Unit.isCompletedTrainer get() = isCompleted() || type == UnitType.Zerg_Lair || type == UnitType.Zerg_Hive

fun Unit.framesTo(to: Position) = ceil((if (isFlying) getDistance(to) else map.getPathLength(position, to)) / type.topSpeed()).toInt()

val Unit.topLeft get() = Position(left, top)
val Unit.bottomRight get() = Position(right, bottom)