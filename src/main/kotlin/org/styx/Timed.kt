package org.styx

class Timed(val name: String? = null) {
    private val started = System.nanoTime()
    private var stopped: Long? = null

    fun stop(): Long {
        stopped = stopped ?: System.nanoTime()
        return stopped!!
    }

    fun ms(): Long = (stop() - started) / 1_000_000

    companion object {
        fun time(name: String, delegate: () -> Any): Timed {
            val timed = Timed(name)
            try {
                delegate()
            } finally {
                timed.stop()
            }
            return timed
        }
    }
}