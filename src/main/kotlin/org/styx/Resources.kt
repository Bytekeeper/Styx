package org.styx

class UnitLock(val criteria: (List<SUnit>) -> Boolean = { true }, val candidates: () -> Iterable<SUnit>) {
    lateinit var units: List<SUnit>
        private set

    fun acquire() {
        if (::units.isInitialized && criteria(units)) {
            if (Styx.resources.tryReserveUnits(units)) {
                units.forEach { it.controller.reset() }
                return
            }
        }
        units = candidates().toList()
        require(criteria(units))
        Styx.resources.reserveUnits(units)
        units.forEach { it.controller.reset() }
    }
}

class ResourceLock {

}

class Resources {
    val availableUnits = mutableListOf<SUnit>()
    private var availableMinerals = 0
    private var availableGas = 0
    private var availableSupply = 0

    fun update() {
        availableGas = Styx.self.gas()
        availableMinerals = Styx.self.minerals()
        availableSupply = Styx.self.supplyTotal() - Styx.self.supplyUsed()
        availableUnits.clear()
        availableUnits.addAll(Styx.units.mine)
    }

    fun tryReserveUnits(units: List<SUnit>): Boolean {
        if (availableUnits.containsAll(units)) {
            availableUnits -= units
            return true
        }
        return false
    }

    fun reserveUnits(units: List<SUnit>) {
        require(availableUnits.containsAll(units))
        availableUnits -= units
    }
}