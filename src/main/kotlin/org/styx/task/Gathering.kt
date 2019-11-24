package org.styx.task

import bwapi.UnitType
import org.styx.*
import org.styx.Styx.economy
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.action.BasicActions
import kotlin.math.min

class Gathering(private val onlyRequiredGas: Boolean = false) : BTNode() {
    private val workersLock = UnitLocks { Styx.resources.availableUnits.filter { it.unitType.isWorker } }
    private val assignment = mutableMapOf<SUnit, SUnit>()

    override fun tick(): NodeStatus {
        workersLock.reacquire()
        if (workersLock.units.isEmpty()) // No workers left? We have serious problems
            return NodeStatus.RUNNING

        val availableGMS = resources.availableGMS
        val gatherGas = availableGMS.gas < 0 && units.myWorkers.count() > 6 || !onlyRequiredGas
        val workers = workersLock.units

        val futureResources = economy.estimatedAdditionalGMSIn(24 * 80) +
                availableGMS
        val extractors = units.myCompleted(UnitType.Zerg_Extractor)
        val gasWorkers = workers.count { it.gatheringGas }
        val missingGasWorkers = min((if (gatherGas) extractors.size * 3 else 0) - gasWorkers,
                if (futureResources.minerals > availableGMS.gas)
                    workers.size
                else if (futureResources.minerals < availableGMS.gas - 200)
                    -gasWorkers
                else
                    0
        )
        if (missingGasWorkers > 0) {
            workers.take(missingGasWorkers)
                    .forEach { worker ->
                        val target = extractors.nearest(worker.x, worker.y) ?: return@forEach
                        if (worker.target != target) {
                            assignment[worker] = target
                        }
                    }
        } else if (missingGasWorkers < 0) {
            assignment.entries.filter { it.value.unitType.isRefinery }
                    .take(-missingGasWorkers)
                    .forEach {
                        assignment.remove(it.key)
                    }
        }
        assignment.entries.removeIf { (u, m) -> !u.exists || !workers.contains(u) || !m.exists }
        workers.forEach { worker ->
            if (worker.orderTarget?.unitType?.isResourceDepot == true)
                return@forEach
            if (assignment[worker] == null) {
                val target = units.minerals.inRadius(worker.x, worker.y, 300) { mineral ->
                    assignment.values.count { it == mineral } == 0
                }.minBy { it.distanceTo(worker) } ?: units.minerals.nearest(worker.x, worker.y) { mineral ->
                    assignment.values.count { it == mineral } < 2
                } ?: return@forEach
                assignment[worker] = target
            }
            val assigned = assignment[worker] ?: return@forEach
            if (worker.orderTarget?.unitType?.isResourceContainer != false && assigned != worker.orderTarget) {
                BasicActions.gather(worker, assigned)
            }
        }
        return NodeStatus.RUNNING
    }
}