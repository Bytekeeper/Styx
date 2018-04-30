package org.fttbot.task

import com.badlogic.gdx.math.Vector2
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.*
import org.fttbot.task.Actions.flee
import org.openbw.bwapi4j.unit.*
import kotlin.math.max
import kotlin.math.min

const val RESOURCE_RANGE = 300

object Workers {
    fun returnWanderingWorkers(): Node {
        return fallback(
                DispatchParallel("Send workers home", { Board.resources.units.filterIsInstance(Worker::class.java) }) {
                    val reachBoard = ReachBoard(tolerance = 32)
                    sequence(
                            Inline("Home Vector") {
                                val force = Vector2()
                                Potential.addWallRepulsion(force, it, 2.3f)
                                Potential.addSafeAreaAttraction(force, it, 0.7f)
                                force.nor()
                                reachBoard.position = it.position + force.scl(64f).toPosition()
                                NodeStatus.SUCCEEDED
                            },
                            Delegate { Actions.reach(it, reachBoard) }
                    )
                },
                Sleep
        )
    }

    fun avoidDamageToWorkers(): Node {
        return fallback(
                DispatchParallel("Avoid damage to workers", { Board.resources.units.filterIsInstance(Worker::class.java) }) {
                    fallback(
                            sequence(
                                    Condition("Threatened?") { it.canBeAttacked() },
                                    flee(it),
                                    ReserveUnit(it)
                            ),
                            Sleep
                    )
                },
                Sleep
        )
    }

}

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

            val gasMissing = min(refineries.size * 3, max(0, workersToAssign.count() - 3)) - workersToAssign.count { it.isGatheringGas }
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
