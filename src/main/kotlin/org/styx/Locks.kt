package org.styx

import bwapi.TilePosition
import org.bk.ass.bt.TreeNode
import org.bk.ass.manage.*
import org.bk.ass.query.PositionQueries

class UnitLocks(criteria: (Collection<SUnit>) -> Boolean = { true }, selector: () -> Collection<SUnit>) : ListLock<SUnit>(UnitListReservation, { selector().toMutableList() }) {
    init {
        setCriteria(criteria)
    }
}

class UnitLock(criteria: (SUnit) -> Boolean = { true }, selector: () -> SUnit?) : Lock<SUnit>(UnitReservation, selector) {
    init {
        setCriteria { criteria(it) && UnitReservation.isAvailable(it) }
    }
}


object ResourceReservation : GMSReservation(Styx.economy::estimatedAdditionalGMSIn)
object TileReservation : BlacklistReservation<TilePosition>()
object UnitReservation : WhiteListReservation<SUnit, PositionQueries<SUnit>>()
object UnitListReservation : ListReservation<SUnit>(UnitReservation)

val costLocks = CostLocks(ResourceReservation)

class TileLock(criteria: (TilePosition) -> Boolean = { true }, selector: () -> TilePosition?) : Lock<TilePosition>(TileReservation, selector) {
    init {
        setCriteria(criteria)
    }
}