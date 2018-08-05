package org.fttbot

import org.fttbot.FTTBot.self
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.PlayerUnit

object Board {
    val resources = Resources()
    val pendingLocations = ArrayList<TilePosition>()
    val pendingUnits = ArrayList<UnitType>()

    fun reset() {
        Board.resources.reset(self.minerals(), self.gas(), self.supplyTotal() - self.supplyUsed(), UnitQuery.myUnits.filter { it.isCompleted })
        pendingUnits.clear()
        pendingLocations.clear()
    }
}

class Resources {
    private val _units: MutableList<PlayerUnit> = ArrayList()
    val units: List<PlayerUnit> get() = _units
    var minerals: Int = 0
        private set
    var gas: Int = 0
        private set
    var supply: Int = 0
        private set

    fun canAfford(minerals: Int, gas: Int, supply: Int) =
            (minerals == 0 || this.minerals >= minerals) &&
                    (gas == 0 || this.gas >= gas) &&
                    (supply == 0 || this.supply >= supply)

    fun canAfford(unitType: UnitType) = canAfford(unitType.mineralPrice(), unitType.gasPrice(), unitType.supplyRequired())

    fun reserveUnits(toReserve: Collection<PlayerUnit>): Resources {
        require(_units.containsAll(toReserve))
        _units.removeAll(toReserve)
        return this
    }

    fun reserve(minerals: Int = 0, gas: Int = 0, supply: Int = 0): Resources {
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

    fun reserveUnit(unit: PlayerUnit): Resources {
        require(_units.contains(unit))
        _units.remove(unit)
        return this
    }

    fun reset(minerals: Int, gas: Int, supply: Int, units: Collection<PlayerUnit>) {
        this.minerals = minerals
        this.gas = gas
        this.supply = supply
        this._units.clear()
        this._units.addAll(units)
    }

    fun reserve(unitType: UnitType) = reserve(unitType.mineralPrice(), unitType.gasPrice(), unitType.supplyRequired())

    fun acquireFor(unitType: UnitType) =
            if (!Board.resources.canAfford(unitType)) {
                Board.resources.reserve(unitType)
                false
            } else {
                Board.resources.reserve(unitType)
                true
            }
}

