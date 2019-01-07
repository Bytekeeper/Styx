package org.styx

class LazyOnFrame<T>(val initializer: () -> T) : Lazy<T> {
    private var lastFrame: Int = -2
    private var _value: T? = null

    override val value: T
        get() {
            val currentFrame = Context.frameCount
            if (currentFrame != lastFrame) {
                _value = initializer()
                lastFrame = currentFrame
            }
            return _value!!
        }

    override fun isInitialized(): Boolean = true
}