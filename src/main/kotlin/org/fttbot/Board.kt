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

    fun reserveMinerals(toReserve: Int): Resources {
        minerals -= toReserve
        return this
    }

    fun reserveGas(toReserve: Int): Resources {
        gas -= toReserve
        return this
    }

    fun reserveUnit(unit: PlayerUnit): Resources {
        require(_units.contains(unit))
        _units.remove(unit)
        return this
    }

    fun reserveSupply(toReserve: Int) : Resources {
        supply -= toReserve
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

