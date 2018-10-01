package org.fttbot

import org.fttbot.task.Task
import org.openbw.bwapi4j.unit.PlayerUnit

interface Variable<T> {
    fun get(): T
    fun isDirty(): Boolean

    fun <E : Any> map(): DVariable<E, T> = DVariable(this)
}

class EachFrame {
    private var lastFrame = -1

    fun whenChanged(execute: () -> Unit) {
        if (lastFrame != FTTBot.frameCount) {
            lastFrame = FTTBot.frameCount
            execute()
        }
    }
}

class DVariable<T : Any, E>(private val other: Variable<E>) : Variable<T> {
    private lateinit var value: T

    override fun get(): T = value

    fun compute(map: (E) -> T): T? {
        if (other.isDirty() || !::value.isInitialized) {
            value = map(other.get())
        }
        return value
    }

    override fun isDirty(): Boolean = other.isDirty()
}

open class Locked<T>(task: Task, val invariant: (T) -> Boolean = { true }) : Variable<T> {
    var entity: T? = null
        private set
    protected var locked = false
    private var dirty = false

    init {
        task.locks += this
    }


    override fun isDirty(): Boolean = dirty

    fun compute(supplier: ((T) -> Boolean) -> T?): T? {
        if (entity == null || !locked && !invariant(entity!!)) {
            dirty = true
            entity = supplier(invariant)
            if (entity != null && !invariant(entity!!)) {
                throw IllegalStateException("Supplied entity breaks invariant!")
            }
        }
        return entity?.let { get() }
    }

    override fun get(): T {
        val result = entity ?: throw java.lang.IllegalStateException("Entity missing")
        locked = true
        return result
    }

    fun set(value: T) {
        locked = true
        dirty = true
        entity = value
    }

    open fun release() {
        if (!locked) {
            entity = null
        }
        dirty = false
        locked = false
    }
}


class UnitLocked<T : PlayerUnit> internal constructor(task: Task, invariant: (T) -> Boolean = { true })
    : Locked<T>(task, { it.exists() && ResourcesBoard.units.contains(it) && invariant(it) }) {

    private val eachFrame = EachFrame()

    override fun get(): T {
        val result = super.get()
        eachFrame.whenChanged {
            ResourcesBoard.reserveUnit(result)
        }
        return result
    }

    override fun release() {
        if (locked && entity!!.exists() && !ResourcesBoard.units.contains(entity!!)) {
            ResourcesBoard.release(entity!!)
        }
        super.release()
    }
}
