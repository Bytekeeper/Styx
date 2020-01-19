package org.styx.task

import org.bk.ass.bt.TreeNode
import org.styx.Styx
import org.styx.Styx.units
import org.styx.UnitLocks
import org.styx.action.BasicActions

object WorkerAvoidDamage : TreeNode() {
    private val workersLock = UnitLocks { Styx.resources.availableUnits.filter { it.unitType.isWorker && it.engaged.isNotEmpty() } }

    override fun exec() {
        running()
        workersLock.reacquire()
        if (workersLock.item.isEmpty()) {// No workers left? We have serious problems
            return
        }

        workersLock.item.forEach { worker ->
            val targetMineral = units.minerals.nearest(worker.x, worker.y) { mineral ->
                worker.engaged.map { it.distanceTo(mineral) - it.maxRangeVs(worker) }.min()!! > worker.unitType.width()
            } ?: return
            BasicActions.gather(worker, targetMineral)
        }
    }

}