package org.fttbot.task

import org.fttbot.ProductionBoard
import org.fttbot.ResourcesBoard
import org.fttbot.info.MyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.info.isMyUnit
import org.openbw.bwapi4j.unit.*
import kotlin.math.max
import kotlin.math.min

private const val RESOURCE_RANGE = 300

class GatherMinerals : Task() {
    override val utility: Double
        get() = 0.1

    override fun toString(): String = "Gather minerals"

    override fun processInternal(): TaskStatus {
        val available = ResourcesBoard
        val myBases = MyInfo.myBases.filterIsInstance<PlayerUnit>()
                .filter { it as ResourceDepot; it.isReadyForResources }
        val units = available.units

        val workerUnits = myBases.flatMap { base ->
            val relevantUnits = UnitQuery.inRadius(base.position, RESOURCE_RANGE)
            val refineries = relevantUnits.filter { it is GasMiningFacility && it.isMyUnit && it.isCompleted }.map { it as GasMiningFacility }
            val remainingWorkers = units.filter { it.getDistance(base) < RESOURCE_RANGE && it is Worker && it.isMyUnit && it.isCompleted }.map { it as Worker }
            val workersToAssign = remainingWorkers.toMutableList()
            val minerals = relevantUnits.filterIsInstance(MineralPatch::class.java).toMutableList()

            val gasMissing = min(refineries.size * 3, max(0, workersToAssign.count() - 3)) - workersToAssign.count { it.isGatheringGas }
            if (gasMissing > 0) {
                val refinery = refineries.first()
                repeat(gasMissing) { _ ->
                    val worker = workersToAssign.firstOrNull { !it.isCarryingMinerals && !it.isGatheringGas }
                            ?: workersToAssign.firstOrNull { !it.isGatheringGas } ?: return@repeat
                    worker.gather(refinery)
                    workersToAssign.remove(worker)
                }
            } else if (gasMissing < 0) {
                repeat(-gasMissing) { _ ->
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
        return TaskStatus.DONE

    }

    companion object : TaskProvider {
        override fun invoke(): List<Task> = listOf(GatherMinerals())
    }
}