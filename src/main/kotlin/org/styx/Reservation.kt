package org.styx

import bwapi.TechType
import bwapi.Unit
import bwapi.UnitType
import org.styx.Context.economy
import org.styx.Context.find
import org.styx.Context.self

class Reservation {
    private val _units = mutableSetOf<Unit>()
    val units: Set<Unit> get() = _units
    var minerals: Int = 0
        private set
    var gas: Int = 0
        private set
    var supply: Int = 0
        private set

    fun canAfford(minerals: Int, gas: Int, supply: Int = 0, futureFrames: Int = 0) =
            (minerals == 0 || this.minerals + economy.mineralsPerFramePerWorker * futureFrames * find.myWorkers.count { it.isGatheringMinerals } >= minerals) &&
                    (gas == 0 || this.gas + economy.gasPerFramePerWorker * futureFrames * find.myWorkers.count { it.isGatheringGas } >= gas) &&
                    (supply == 0 || this.supply >= supply)

    fun canAfford(unitType: UnitType, futureFrames: Int = 0) = canAfford(unitType.mineralPrice(), unitType.gasPrice(), unitType.supplyRequired(), futureFrames)

    fun reserveUnits(toReserve: Collection<Unit>): Reservation {
        require(_units.containsAll(toReserve))
        _units.removeAll(toReserve)
        return this
    }

    fun reserve(minerals: Int = 0, gas: Int = 0, supply: Int = 0): Reservation {
        this.minerals -= minerals
        this.gas -= gas
        this.supply -= supply
        return this
    }

    fun release(unit: Unit) {
        require(!_units.contains(unit))
        require(unit.exists())
        _units += unit
    }

    fun reserveUnit(unit: Unit): Reservation {
        require(_units.contains(unit))
        _units.remove(unit)
        return this
    }

    fun update() {
        this.minerals = self.minerals()
        this.gas = self.gas()
        this.supply = self.supplyTotal() - self.supplyUsed()
        this._units.clear()
        this._units.addAll(find.myUnits.filter { it.type.canMove() || it.type.canAttack() || it.type.canProduce() || it.type.upgradesWhat().isNotEmpty() || it.type.researchesWhat().isNotEmpty() })
    }

    fun reset() {

    }

    fun acquire(minerals: Int = 0, gas: Int = 0, supply: Int = 0, futureFrames: Int = 0) =
            canAfford(minerals, gas, supply, futureFrames)
                    .also { reserve(minerals, gas, supply) }

    fun acquireFor(unitType: UnitType, futureFrames: Int = 0) =
            acquire(unitType.mineralPrice(), unitType.gasPrice(), unitType.supplyRequired(), futureFrames)

    fun acquireFor(type: TechType) = acquire(type.mineralPrice(), type.gasPrice())
}


