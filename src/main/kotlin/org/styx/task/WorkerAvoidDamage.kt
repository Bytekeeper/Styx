package org.styx.task

import org.bk.ass.bt.TreeNode
import org.styx.UnitLocks
import org.styx.UnitReservation
import org.styx.micro.Force

object WorkerAvoidDamage : TreeNode() {
    private val workersLock = UnitLocks { UnitReservation.availableItems.filter { it.unitType.isWorker && it.engaged.isNotEmpty() } }

    override fun exec() {
        running()
        workersLock.reacquire()
        if (workersLock.item.isEmpty()) {// No workers left? We have serious problems
            return
        }

        workersLock.item.forEach { worker ->
            val force = Force().avoidDanger(worker, 64)
                    .collisionRepulsion(worker, 0.3)
            force.apply(worker)
        }
    }

}