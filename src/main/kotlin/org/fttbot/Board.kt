package org.fttbot

import org.openbw.bwapi4j.unit.PlayerUnit

object Board {
    val resources = Resources()
}

class Resources {
    private val _units: MutableCollection<PlayerUnit> = ArrayList()
    val units: Collection<PlayerUnit> get() = _units
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
            mineralPrice <= minerals && gasPrice <= gas && (supply == 0 || supply <= this.supply)

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

    fun enoughMineralsAndGas(): Boolean = minerals >= 0 && gas >= 0
    fun enough(): Boolean = minerals >= 0 && gas >= 0 && supply >= 0
}

