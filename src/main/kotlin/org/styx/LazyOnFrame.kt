package org.styx

import org.bk.ass.FrameLocal

class LazyOnFrame<T>(val initializer: () -> T) : Lazy<T> {
    private val frameLocal = FrameLocal(Styx::frame, initializer)

    override val value: T
        get() = frameLocal.get()

    override fun isInitialized(): Boolean = true
}