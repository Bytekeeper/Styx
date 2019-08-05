package org.styx

import bwapi.TechType
import bwapi.UnitFilter
import bwapi.UnitType
import bwapi.UpgradeType
import org.bk.ass.query.UnitFinder
import kotlin.math.max

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

class GMS(val gas: Int, val minerals: Int, val supply: Int) {
    operator fun minus(sub: GMS) = GMS(gas - sub.gas, minerals - sub.minerals, supply - sub.supply)
    fun gtOrEq(other: GMS) = max(gas, 0) >= other.gas && max(minerals, 0) >= other.minerals && max(supply, 0) >= other.supply
}

class Resources {
    val availableUnits = UnitFinder(positionAndIdExtractor)
    private var availableGMS = GMS(0, 0, 0)

    fun update() {
        availableGMS = GMS(Styx.self.gas(), Styx.self.minerals(), Styx.self.supplyTotal() - Styx.self.supplyUsed())
        availableUnits.clear()
        availableUnits.addAll(Styx.units.mine)
    }

    fun tryReserveUnits(units: Collection<SUnit>): Boolean =
            if (availableUnits.containsAll(units)) {
                availableUnits -= units
                true
            } else false

    fun tryReserveGMS(gms: GMS): Boolean =
            if (availableGMS.gtOrEq(gms)) {
                availableGMS -= gms
                true
            } else false

    fun reserveGMS(gms: GMS) {
        availableGMS -= gms
    }

    fun tryReserveUnit(unit: SUnit): Boolean = availableUnits.remove(unit)

    fun releaseUnits(units: Collection<SUnit>) {
        availableUnits += units
    }

    fun releaseUnit(unit: SUnit) {
        availableUnits += unit
    }
}