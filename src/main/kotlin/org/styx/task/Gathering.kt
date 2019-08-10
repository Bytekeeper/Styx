package org.styx.task

import bwapi.UnitType
import org.styx.*
import org.styx.action.BasicActions

object Gathering : BTNode() {
    private val workers = UnitLocks { Styx.resources.availableUnits.filter { it.unitType.isWorker } }

    override fun tick(): TickResult {
        workers.reacquire()
        if (workers.units.isEmpty()) // No workers left? We have serious problems
            return TickResult.RUNNING
        val pending = workers.units.toMutableList()
        val extractors = Styx.units.myCompleted(UnitType.Zerg_Extractor)
        val missingGasWorkers = extractors.size * 3 - pending.count { it.gatheringGas }
        pending.take(missingGasWorkers)
                .forEach { worker ->
                    val target = extractors.closestTo(worker.x, worker.y).orNull()
                            ?: return@forEach
                    if (worker.target != target && worker.target?.unitType?.isResourceDepot != true) {
                        BasicActions.gather(worker, target)
                    }
                }

        workers.units.forEach { worker ->
            if (worker.target?.unitType?.isResourceContainer != true && worker.target?.unitType?.isResourceDepot != true) {
                val target = Styx.units.minerals.closestTo(worker.x, worker.y) { !it.beingGathered }.orNull()
                        ?: return@forEach
                BasicActions.gather(worker, target)
            }
        }
        return TickResult.RUNNING
    }
}