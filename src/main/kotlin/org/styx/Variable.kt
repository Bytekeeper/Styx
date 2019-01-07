package org.styx

import bwapi.TilePosition
import bwapi.Unit
import org.styx.Context.frameCount
import org.styx.Context.reserve

open class Lock<U>(private val invariant: (U) -> Boolean = { true }) {
    private var lastValue: U? = null
    val current: U? get() = lastValue

    open fun relock(): U = lockOr { null }!!

    open fun lockOr(provider: () -> U?): U? {
        lastValue?.let {
            if (invariant(it)) return it
        }
        lastValue = provider()
        require(lastValue == null || invariant(lastValue!!))
        {
            "BAD"
        }
        return lastValue
    }

    open fun reset() {
        lastValue = null
    }

    open fun release() {
        lastValue = null
    }
}

class TilePositionLock(invariant: (TilePosition) -> Boolean) : Lock<TilePosition>(invariant)

class UnitLock(invariant: (Unit) -> Boolean = { true }) : Lock<Unit>({ u -> Context.reserve.units.contains(u) && invariant(u) }) {
    private var lastLockFrame = -1

    override fun lockOr(provider: () -> Unit?): Unit? =
            super.lockOr(provider)
                    ?.also { unit ->
                        if (lastLockFrame != frameCount) {
                            reserve.reserveUnit(unit)
                            lastLockFrame = frameCount
                        }
                    }

    override fun reset() {
        super.reset()
        lastLockFrame = -1
    }

    override fun release() {
        current?.let { reserve.release(it) }
        super.release()
    }
}
