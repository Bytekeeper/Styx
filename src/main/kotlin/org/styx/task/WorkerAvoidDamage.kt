package org.styx.task

import org.bk.ass.bt.TreeNode
import org.styx.Styx.units
import org.styx.UnitLocks
import org.styx.UnitReservation
import org.styx.action.BasicActions
import org.styx.micro.Potential
import org.styx.plus
import org.styx.times

object WorkerAvoidDamage : TreeNode() {
    private val workersLock = UnitLocks { UnitReservation.availableItems.filter { it.unitType.isWorker && it.engaged.isNotEmpty() } }

    override fun exec() {
        running()
        workersLock.reacquire()
        if (workersLock.item.isEmpty()) {// No workers left? We have serious problems
            return
        }

        workersLock.item.forEach { worker ->
            val targetMineral = units.minerals.nearest(worker.x, worker.y) { mineral ->
                worker.engaged.map { it.distanceTo(mineral) - it.maxRangeVs(worker) }.min()!! > worker.unitType.width() * 4
            }
            if (targetMineral != null && !worker.carrying) {
                BasicActions.gather(worker, targetMineral)
            } else {
                val force = Potential.avoidDanger(worker, 64) +
                        Potential.collisionRepulsion(worker) * 0.3
                Potential.apply(worker, force)
            }
        }
    }

}