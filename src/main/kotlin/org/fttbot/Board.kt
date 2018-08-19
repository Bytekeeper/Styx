package org.fttbot

import org.fttbot.FTTBot.self
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.PlayerUnit

object ProductionBoard {
    val pendingLocations = ArrayList<TilePosition>()
    val pendingUnits = ArrayList<UnitType>()

    fun reset() {
        pendingUnits.clear()
        pendingLocations.clear()
    }
}

object ResourcesBoard {
    private val _units: MutableList<PlayerUnit> = ArrayList()
    val units: List<PlayerUnit> get() = _units
    var minerals: Int = 0
        private set
    var gas: Int = 0
        private set
    var supply: Int = 0
        private set

    fun canAfford(minerals: Int, gas: Int, supply: Int = 0) =
            (minerals == 0 || this.minerals >= minerals) &&
                    (gas == 0 || this.gas >= gas) &&
                    (supply == 0 || this.supply >= supply)

    fun canAfford(unitType: UnitType) = canAfford(unitType.mineralPrice(), unitType.gasPrice(), unitType.supplyRequired())

    fun reserveUnits(toReserve: Collection<PlayerUnit>): ResourcesBoard {
        require(_units.containsAll(toReserve))
        _units.removeAll(toReserve)
        return this
    }

    fun reserve(minerals: Int = 0, gas: Int = 0, supply: Int = 0): ResourcesBoard {
        this.minerals -= minerals
        this.gas -= gas
        this.supply -= supply
        return this
    }

    fun release(minerals: Int, gas: Int, supply: Int) {
        this.minerals += minerals
        this.gas += gas
        this.supply += supply
    }

    fun reserveUnit(unit: PlayerUnit): ResourcesBoard {
        require(_units.contains(unit))
        _units.remove(unit)
        return this
    }

    fun reset() {
        this.minerals = self.minerals()
        this.gas = self.gas()
        this.supply = self.supplyTotal() - self.supplyUsed()
        this._units.clear()
        this._units.addAll(UnitQuery.myUnits.filter { it.isCompleted })
    }

    fun reserve(unitType: UnitType) = reserve(unitType.mineralPrice(), unitType.gasPrice(), unitType.supplyRequired())

    fun acquireFor(unitType: UnitType) =
            if (!canAfford(unitType)) {
                reserve(unitType)
                false
            } else {
                reserve(unitType)
                true
            }
}

