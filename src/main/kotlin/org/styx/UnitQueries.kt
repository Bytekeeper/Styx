package org.styx

import bwapi.Position
import bwapi.Unit
import bwapi.UnitType
import org.bk.ass.query.PositionAndId
import org.bk.ass.query.UnitFinder
import org.styx.Context.game
import org.styx.Context.self

fun UnitFinder<Unit>.inRadius(position: Position, radius: Int) = inRadius(position.x, position.y, radius)

class UnitQueries {
    lateinit var allUnits: UnitFinder<Unit>
        private set
    lateinit var groundUnits: UnitFinder<Unit>
        private set
    lateinit var myUnits: UnitFinder<Unit>
        private set
    lateinit var myResourceDepots: UnitFinder<Unit>
        private set
    lateinit var myTrainers: UnitFinder<Unit>
        private set
    lateinit var myWorkers: UnitFinder<Unit>
        private set
    lateinit var minerals: UnitFinder<Unit>
        private set
    lateinit var geysers: UnitFinder<Unit>
        private set
    lateinit var myBuildings: UnitFinder<Unit>
        private set
    lateinit var enemyUnits: UnitFinder<Unit>
        private set
    private val myUnitsOfType = mutableMapOf<UnitType, UnitFinder<Unit>>()

    fun reset() {
        allUnits = MyUnitFinder(game.allUnits.toList())
        minerals = MyUnitFinder(game.allUnits.filter { it.type.isMineralField })
        geysers = MyUnitFinder(game.allUnits.filter { it.type == UnitType.Resource_Vespene_Geyser })
    }

    fun update() {
        allUnits = MyUnitFinder(game.allUnits.toList())
        groundUnits = MyUnitFinder(allUnits.filter { !it.isFlying })
        myUnits = MyUnitFinder(game.allUnits.filter { it.player == self })
        enemyUnits = MyUnitFinder(game.allUnits.filter { it.player != self && !it.player.isNeutral })
        myBuildings = MyUnitFinder(myUnits.filter { it.type.isBuilding })
        myTrainers = MyUnitFinder(myBuildings.filter { it.canTrain() })
        myResourceDepots = MyUnitFinder(myUnits.filter {
            when (it.type) {
                UnitType.Terran_Command_Center, UnitType.Zerg_Hatchery, UnitType.Zerg_Hive, UnitType.Zerg_Lair,
                UnitType.Protoss_Nexus -> true
                else -> false
            }
        })
        myWorkers = MyUnitFinder(myUnits.filter { it.type.isWorker })
        myUnitsOfType.clear()
    }

    fun my(type: UnitType) = myUnitsOfType.computeIfAbsent(type) { MyUnitFinder(myUnits.filter { it.type == type }) }

    class MyUnitFinder(units: List<Unit>) : UnitFinder<Unit>(units, { PositionAndId(it.id, it.x, it.y) })
}
