package org.styx

class LazyOnFrame<T>(val initializer : () -> T) : Lazy<T> {
    private var lastFrame : Int = -1
    private var _value : T? = null

    override val value: T
        get() {
            val currentFrame = Styx.frame
            if (currentFrame != lastFrame) {
                _value = initializer()
                lastFrame = currentFrame
            }
            return _value!!
        }

    override fun isInitialized(): Boolean = true
}