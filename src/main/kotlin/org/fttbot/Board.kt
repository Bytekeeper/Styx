package org.fttbot

import org.fttbot.FTTBot.self
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.PlayerUnit

object Board {
    val resources = Resources()

    val pendingUnits = ArrayList<UnitType>()

    fun reset() {
        Board.resources.reset(self.minerals(), self.gas(), self.supplyTotal() - self.supplyUsed(), UnitQuery.myUnits.filter { it.isCompleted })
        pendingUnits.clear()
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

    fun reserveUnits(toReserve: Collection<PlayerUnit>): Resources {
        require(_units.containsAll(toReserve))
        _units.removeAll(toReserve)
        return this
    }

    fun isAvailable(mineralPrice: Int, gasPrice: Int, supply: Int = 0): Boolean =
            (mineralPrice == 0 || mineralPrice <= minerals) &&
                    (gasPrice == 0 || gasPrice <= gas) &&
                    (supply == 0 || supply <= this.supply)

    fun reserve(minerals: Int = 0, gas: Int = 0, supply: Int = 0): Resources {
        this.minerals -= minerals
        this.gas -= gas
        this.supply -= supply
        return this
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

    fun enough(): Boolean = minerals >= 0 && gas >= 0 && supply >= 0
}

