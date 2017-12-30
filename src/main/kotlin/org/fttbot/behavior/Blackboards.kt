package org.fttbot.behavior

import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Unit
import java.util.*

open class BBUnit(val unit: PlayerUnit) {

    companion object {
        private val boards = HashMap<PlayerUnit, BBUnit>()

        fun of(unit: PlayerUnit) = boards.computeIfAbsent(unit) { BBUnit(unit) }
        fun destroy(unit: PlayerUnit) = boards.remove(unit)
        fun all() = boards.values
    }

    var moveTarget: Position? = null
    var targetResource: Unit? = null
    var status: String = "<Nothing>"
    var construction: Construction? = null
    var scouting: Scouting? = null
    var attacking: Attacking? = null
    var combatSuccessProbability: Double = 0.5
}

class Construction(val type: UnitType, var position: TilePosition) {
    // Confirmed by the engine to have started
    var started: Boolean = false
    // Worker called "build"
    var commissioned: Boolean = false
    var building: Building? = null
}

class Scouting(val locations: Deque<Position>) {
    var points: List<Position>? = null
    var index: Int = 0
}

class Attacking(val target: Unit)

object ProductionBoard {
    val orderedConstructions = ArrayDeque<Construction>()
    val queue = ArrayDeque<Item>()
    var reservedMinerals = 0
    var reservedGas = 0

    fun updateReserved() {
        reservedMinerals = 0
        reservedGas = 0
        orderedConstructions.filter { !it.started }
                .forEach {
                    reservedMinerals += it.type.mineralPrice()
                    reservedGas += it.type.gasPrice()
                }
    }

    class Item(val type: UnitType, val favoriteBuilder: Unit? = null)
}

object ScoutingBoard {
    var lastScoutFrameCount: Int = 0
}