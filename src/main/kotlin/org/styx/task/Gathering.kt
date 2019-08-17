package org.styx.task

import bwapi.UnitType
import org.styx.BTNode
import org.styx.NodeStatus
import org.styx.Styx
import org.styx.UnitLocks
import org.styx.action.BasicActions
import kotlin.math.min

object Gathering : BTNode() {
    private val workers = UnitLocks { Styx.resources.availableUnits.filter { it.unitType.isWorker } }

    override fun tick(): NodeStatus {
        workers.reacquire()
        if (workers.units.isEmpty()) // No workers left? We have serious problems
            return NodeStatus.RUNNING

        val pending = workers.units.toMutableList()
        val extractors = Styx.units.myCompleted(UnitType.Zerg_Extractor)
        val missingGasWorkers = min(extractors.size * 3 - pending.count { it.gatheringGas }, pending.size)
        pending.take(missingGasWorkers)
                .forEach { worker ->
                    val target = extractors.nearest(worker.x, worker.y) ?: return@forEach
                    if (worker.target != target && worker.target?.unitType?.isResourceDepot != true) {
                        BasicActions.gather(worker, target)
                    }
                }

        workers.units.forEach { worker ->
            if (worker.target?.unitType?.isResourceContainer != true && worker.target?.unitType?.isResourceDepot != true) {
                val target = Styx.units.minerals.nearest(worker.x, worker.y) { !it.beingGathered } ?: return@forEach
                BasicActions.gather(worker, target)
            }
        }
        return NodeStatus.RUNNING
    }
}