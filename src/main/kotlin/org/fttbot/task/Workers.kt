package org.fttbot.task

import org.fttbot.BaseNode
import org.fttbot.Board
import org.fttbot.NodeStatus
import org.fttbot.info.MyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.info.isMyUnit
import org.fttbot.info.isReadyForResources
import org.openbw.bwapi4j.unit.*
import kotlin.math.max

const val RESOURCE_RANGE = 300

object GatherResources : BaseNode() {
    override fun tick(): NodeStatus {
        val available = Board.resources
        val myBases = MyInfo.myBases.filterIsInstance(PlayerUnit::class.java).filter { it as Base; it.isReadyForResources }
        val units = available.units

        val workerUnits = myBases.flatMap { base ->
            val relevantUnits = UnitQuery.inRadius(base.position, RESOURCE_RANGE)
            val refineries = relevantUnits.filter { it is GasMiningFacility && it.isMyUnit && it.isCompleted }.map { it as GasMiningFacility }
            val remainingWorkers = units.filter { it.getDistance(base) < RESOURCE_RANGE && it is Worker && it.isMyUnit && it.isCompleted }.map { it as Worker }
            val workersToAssign = remainingWorkers.toMutableList()
            val minerals = relevantUnits.filterIsInstance(MineralPatch::class.java).toMutableList()

            val gasMissing = refineries.size * 3 - workersToAssign.count { it.isGatheringGas } - max(0, 3 - workersToAssign.count())
            if (gasMissing > 0) {
                val refinery = refineries.first()
                repeat(gasMissing) {
                    val worker = workersToAssign.firstOrNull { !it.isCarryingMinerals && !it.isGatheringGas }
                            ?: workersToAssign.firstOrNull { !it.isGatheringGas } ?: return@repeat
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
                    .filter { !it.isGatheringMinerals && !it.isGatheringGas && it.isInterruptible }
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
