package org.styx.task

import org.styx.Styx
import org.styx.Task
import org.styx.UnitLock
import org.styx.action.BasicActions
import org.styx.orNull

object Gathering : Task {
    override val utility: Double
        get() = 0.0

    private val workers = UnitLock { Styx.units.workers }

    override fun execute() {
        workers.acquire()
        if (workers.units.isEmpty()) return
        workers.units.forEach {
            val target = Styx.units.minerals.closestTo(it.x, it.y).orNull() ?: return@forEach
            BasicActions.gather(it, target)
        }
    }
}