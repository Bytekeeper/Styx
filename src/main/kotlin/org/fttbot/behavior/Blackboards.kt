package org.fttbot.behavior

import bwapi.Position
import bwapi.TilePosition
import org.fttbot.import.FUnitType
import org.fttbot.layer.FUnit
import java.util.*

open class BBUnit(val unit: FUnit) {

    companion object {
        private val boards = HashMap<FUnit, BBUnit>()

        fun of(unit: FUnit) = boards.computeIfAbsent(unit) { BBUnit(unit) }
        fun destroy(unit: FUnit) = boards.remove(unit)
        fun all() = boards.values
    }

    var moveTarget: Position? = null
    var targetResource: FUnit? = null
    var status: String = "<Nothing>"
    var construction: Construction? = null
    var scouting: Scouting? = null
    var attacking: Attacking? = null
    var combatSuccessProbability: Double = 0.5
}

class Construction(val type: FUnitType, var position: TilePosition) {
    // Confirmed by the engine to have started
    var started: Boolean = false
    // Worker called "build"
    var commissioned: Boolean = false
    var building: FUnit? = null
}

class Scouting(val locations: Deque<Position>) {
    var points: List<Position>? = null
    var index: Int = 0
}

class Attacking(val target: FUnit)

object ProductionBoard {
    val queue = ArrayDeque<Item>()
    var reservedMinerals = 0
    var reservedGas = 0
    var queueNeedsRebuild = true

    class Item(val type: FUnitType)
}

object ScoutingBoard {
    var lastScoutTime: Int = 0
}