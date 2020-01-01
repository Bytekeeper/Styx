package org.styx

import org.bk.ass.manage.*

class UnitLocks(criteria: (Collection<SUnit>) -> Boolean = { true }, selector: () -> Collection<SUnit>) : ListLock<SUnit>(UnitListReservation, { selector().toMutableList() }) {
    init {
        setCriteria(criteria)
    }
}

class UnitLock(criteria: (SUnit) -> Boolean = { true }, selector: () -> SUnit?) : Lock<SUnit>(UnitReservation, selector) {
    init {
        setCriteria { criteria(it) && Styx.resources.availableUnits.contains(it) }
    }
}

object UnitListReservation : Reservation<List<SUnit>> {
    override fun reserve(lock: Lock<List<SUnit>>, item: List<SUnit>): Boolean =
            Styx.resources.tryReserveUnits(item)

    override fun release(lock: Lock<List<SUnit>>, item: List<SUnit>) {
        Styx.resources.releaseUnits(item)
    }
}

object UnitReservation : Reservation<SUnit> {
    override fun reserve(lock: Lock<SUnit>, item: SUnit): Boolean =
            Styx.resources.tryReserveUnit(item)

    override fun release(lock: Lock<SUnit>, item: SUnit) {
        Styx.resources.releaseUnit(item)
    }
}

object ResourceReservation : Reservation<GMS> {
    override fun reserve(lock: Lock<GMS>, item: GMS): Boolean =
            if (Styx.resources.tryReserveGMS(item))
                true
            else {
                Styx.resources.reserveGMS(item)
                false
            }

    override fun itemAvailableInFuture(lock: Lock<GMS>, item: GMS, futureFrames: Int): Boolean {
        return Styx.resources.availableGMS.plus(item).plus(Styx.economy.estimatedAdditionalGMSIn(futureFrames)).canAfford(item)
    }

    override fun release(lock: Lock<GMS>, item: GMS) {
        Styx.resources.releaseGMS(item)
    }
}

val costLocks = CostLocks(ResourceReservation)

