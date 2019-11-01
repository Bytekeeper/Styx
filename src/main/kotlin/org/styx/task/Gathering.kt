package org.styx.task

import bwapi.UnitType
import org.styx.*
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.action.BasicActions

class Gathering(private val onlyRequiredGas: Boolean = false) : BTNode() {
    private val workers = UnitLocks { Styx.resources.availableUnits.filter { it.unitType.isWorker } }
    private val assignment = mutableMapOf<SUnit, SUnit>()

    override fun tick(): NodeStatus {
        workers.reacquire()
        if (workers.units.isEmpty()) // No workers left? We have serious problems
            return NodeStatus.RUNNING

        val availableGMS = resources.availableGMS
        val gatherGas = availableGMS.gas < 0 && units.myWorkers.count() > 6 || !onlyRequiredGas
        if (gatherGas) {
            val pending = workers.units.toMutableList()
            val extractors = Styx.units.myCompleted(UnitType.Zerg_Extractor)
            val missingGasWorkers = clamp(extractors.size * 3 - pending.count { it.gatheringGas }, 0, pending.size)
            pending.take(missingGasWorkers)
                    .forEach { worker ->
                        val target = extractors.nearest(worker.x, worker.y) ?: return@forEach
                        if (worker.target != target && worker.target?.unitType?.isResourceDepot != true) {
                            BasicActions.gather(worker, target)
                        }
                    }
        }
        workers.units.forEach { worker ->
            if (worker.carrying) {
                assignment.remove(worker)
            } else if (worker.gatheringGas && !gatherGas) {
                worker.stop()
            }
        }
        assignment.entries.removeIf { (u, m) -> !workers.units.contains(u) || !m.exists }
        workers.units.forEach { worker ->
            if (worker.target?.unitType?.isResourceContainer != true && worker.target?.unitType?.isResourceDepot != true) {
                val target = Styx.units.minerals.inRadius(worker.x, worker.y, 300) { mineral ->
                    assignment.values.count { it == mineral } == 0
                }.minBy { it.distanceTo(worker) } ?: Styx.units.minerals.nearest(worker.x, worker.y) { mineral ->
                    assignment.values.count { it == mineral } < 2
                } ?: return@forEach
                assignment[worker] = target
                BasicActions.gather(worker, target)
            }
        }
        return NodeStatus.RUNNING
    }
}