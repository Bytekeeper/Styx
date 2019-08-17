package org.styx

import bwapi.TechType
import bwapi.UnitType
import bwapi.UpgradeType
import org.bk.ass.manage.GMS
import org.bk.ass.query.PositionQueries

abstract class Lock<T : Any>(val criteria: (T) -> Boolean = { true }, val selector: () -> T?) {
    protected var item: T? = null
        private set
    var satisfied = false
        private set
    var changed = false
        private set

    fun reacquire() {
        reset()
        acquire()
    }

    fun release() {
        item?.let { releaseItem(it) }
        item = null
    }

    fun reset() {
        item = null
    }

    fun acquire() {
        changed = false
        if (item != null && criteria(item!!)) {
            if (tryReserve(item!!)) {
                satisfied = true
                return
            }
        }
        item = selector()
        satisfied = item != null && criteria(item!!)
        if (satisfied) {
            changed = true
            val reserved = tryReserve(item!!)
            require(reserved)
        }
    }

    abstract fun releaseItem(item: T)
    abstract fun tryReserve(item: T): Boolean
}

class UnitLocks(criteria: (Collection<SUnit>) -> Boolean = { true }, selector: () -> Collection<SUnit>) : Lock<Collection<SUnit>>(criteria, selector) {
    val units get() = item!!

    override fun tryReserve(item: Collection<SUnit>): Boolean {
        if (Styx.resources.tryReserveUnits(item)) {
            item.forEach { it.controller.reset() }
            return true
        }
        return false
    }

    override fun releaseItem(item: Collection<SUnit>) = Styx.resources.releaseUnits(item)
}

class UnitLock(criteria: (SUnit) -> Boolean = { true }, selector: () -> SUnit?) : Lock<SUnit>(criteria, selector) {
    val unit get() = item

    override fun tryReserve(item: SUnit): Boolean {
        if (Styx.resources.tryReserveUnit(item)) {
            item.controller.reset()
            return true
        }
        return false
    }

    override fun releaseItem(item: SUnit) = Styx.resources.releaseUnit(item)
}

open class GMSLock(val gms: GMS, val futureFrames: Int = 0) {
    var satisfied = false
        private set
    var willBeSatisfied = false
        private set

    fun acquire() {
        if (Styx.resources.tryReserveGMS(gms)) {
            satisfied = true
            willBeSatisfied = true
            return
        }
        Styx.resources.reserveGMS(gms)
        satisfied = false
        willBeSatisfied = false
    }
}

class UnitCostLock(type: UnitType, futureFrames: Int = 0) : GMSLock(GMS(type.gasPrice(), type.mineralPrice(), type.supplyRequired()), futureFrames)
class UpgradeCostLock(type: UpgradeType, level: Int, futureFrames: Int = 0) : GMSLock(GMS(type.gasPrice(level), type.mineralPrice(level), 0), futureFrames)
class TechCostLock(type: TechType, futureFrames: Int = 0) : GMSLock(GMS(type.gasPrice(), type.mineralPrice(), 0), futureFrames)

class Resources {
    lateinit var availableUnits: PositionQueries<SUnit>
    var availableGMS = GMS(0, 0, 0)
        private set

    fun update() {
        availableGMS = GMS(Styx.self.gas(), Styx.self.minerals(), Styx.self.supplyTotal() - Styx.self.supplyUsed())
        availableUnits = PositionQueries(Styx.units.mine, positionExtractor)
    }

    fun tryReserveUnits(units: Collection<SUnit>): Boolean =
            if (availableUnits.containsAll(units)) {
                availableUnits.removeAll(units)
                true
            } else false

    fun tryReserveGMS(gms: GMS): Boolean =
            if (availableGMS.greaterOrEqual(gms)) {
                availableGMS -= gms
                true
            } else false

    fun reserveGMS(gms: GMS) {
        availableGMS -= gms
    }

    fun tryReserveUnit(unit: SUnit): Boolean = availableUnits.remove(unit)

    fun releaseUnits(units: Collection<SUnit>) {
        availableUnits.addAll(units)
    }

    fun releaseUnit(unit: SUnit) {
        availableUnits.add(unit)
    }
}