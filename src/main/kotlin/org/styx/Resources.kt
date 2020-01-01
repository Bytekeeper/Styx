package org.styx

import org.bk.ass.bt.TreeNode
import org.bk.ass.manage.GMS
import org.bk.ass.query.PositionQueries

class Resources : TreeNode() {
    lateinit var availableUnits: PositionQueries<SUnit>
    var availableGMS = GMS(0, 0, 0)
        private set

    override fun exec() {
        success()
        availableGMS = GMS(Styx.self.gas(), Styx.self.minerals(), Styx.self.supplyTotal() - Styx.self.supplyUsed())
        availableUnits = PositionQueries(Styx.units.mine, positionExtractor)
    }

    fun tryReserveUnits(units: Collection<SUnit>): Boolean =
            if (availableUnits.containsAll(units)) {
                availableUnits.removeAll(units)
                true
            } else false

    fun tryReserveGMS(gms: GMS): Boolean =
            if (availableGMS.canAfford(gms)) {
                reserveGMS(gms)
                true
            } else false

    fun reserveGMS(gms: GMS) {
        availableGMS -= gms
    }

    fun releaseGMS(gms: GMS) {
        availableGMS += gms
    }

    fun tryReserveUnit(unit: SUnit): Boolean = availableUnits.remove(unit)

    fun releaseUnits(units: Collection<SUnit>) {
        availableUnits.addAll(units)
    }

    fun releaseUnit(unit: SUnit) {
        availableUnits.add(unit)
    }
}
