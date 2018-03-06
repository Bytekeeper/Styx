package org.fttbot.task

import org.fttbot.*
import org.fttbot.info.UnitQuery
import org.fttbot.info.UnitQuery.minerals
import org.fttbot.info.inRadius
import org.fttbot.info.isMyUnit
import org.openbw.bwapi4j.unit.*

const val RESOURCE_RANGE = 300

object GatherResources : Node {
    override fun tick(): NodeStatus {
        val available = Board.resources
        val myBases = UnitQuery.ownedUnits.filter { it.isMyUnit && it is Base }
        val units = available.units

        val workerUnits = myBases.flatMap { base ->
            val relevantUnits = UnitQuery.inRadius(base.position, RESOURCE_RANGE)
            val refineries = relevantUnits.filter { it is GasMiningFacility && it.isMyUnit && it.isCompleted }.map { it as GasMiningFacility }
            val remainingWorkers = units.filter { it.getDistance(base) < RESOURCE_RANGE && it is Worker && it.isMyUnit && it.isCompleted }.map { it as Worker }
            val workersToAssign = remainingWorkers.toMutableList()
            val minerals = relevantUnits.filterIsInstance(MineralPatch::class.java).toMutableList()

            val gasMissing = refineries.size * 3 - workersToAssign.count { it.isGatheringGas }
            if (gasMissing > 0) {
                val refinery = refineries.first()
                repeat(gasMissing) {
                    val worker = workersToAssign.firstOrNull { !it.isCarryingMinerals }
                            ?: workersToAssign.firstOrNull() ?: return@repeat
                    worker.gather(refinery)
                    workersToAssign.remove(worker)
                }
            } else if (gasMissing < 0) {
                repeat(-gasMissing) {
                    val worker = workersToAssign.firstOrNull { it.isGatheringGas && !it.isCarryingGas }
                            ?: workersToAssign.firstOrNull { it.isGatheringGas } ?: return@repeat
                    val targetMineral = (minerals.filter { !it.isBeingGathered }.minBy { it.getDistance(worker) }
                            ?: minerals.minBy { it.getDistance(worker) })
                    if (targetMineral != null) {
                        worker.gather(targetMineral)
                        minerals.remove(targetMineral)
                        workersToAssign.remove(worker)
                    }
                }
            }
            workersToAssign
                    .filter { !it.isGatheringMinerals && !it.isGatheringGas }
                    .forEach { worker ->
                        if (worker.targetUnit !is MineralPatch) {
                            val targetMineral = (minerals.filter { !it.isBeingGathered }.minBy { it.getDistance(worker) }
                                    ?: minerals.minBy { it.getDistance(worker) })
                            if (targetMineral != null) {
                                worker.gather(targetMineral)
                                minerals.remove(targetMineral)
                            }
                        }
                    }
            return@flatMap remainingWorkers
        }
        available.reserveUnits(workerUnits)
        return NodeStatus.SUCCEEDED
    }
}
