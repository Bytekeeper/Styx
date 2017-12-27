package org.fttbot.behavior

import bwapi.Position
import bwapi.TilePosition
import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.board
import org.fttbot.layer.FUnit
import org.fttbot.translated

fun findWorker(forPosition: Position? = null, maxRange: Double = 400.0): FUnit? {
    var candidates = FUnit.myWorkers()
    if (forPosition != null) {
        candidates = candidates.filter { forPosition.getDistance(it.position) <= maxRange }
    }
    return candidates.minWith(Comparator { a, b ->
        if (a.isIdle != b.isIdle) {
            if (a.isIdle) -1 else 1
        } else if (a.isGatheringMinerals != b.isGatheringMinerals) {
            if (a.isGatheringMinerals) -1 else 1
        } else if ((a.isGatheringMinerals && !a.isCarryingMinerals) != (b.isGatheringMinerals && !b.isCarryingMinerals)) {
            if (a.isGatheringMinerals && !a.isCarryingMinerals) -1 else 1
        } else if (a.isGatheringGas != b.isGatheringGas) {
            if (a.isGatheringGas) -1 else 1
        } else if ((a.isGatheringGas && !a.isCarryingGas) != (b.isGatheringGas && !b.isCarryingGas)) {
            if (a.isGatheringGas && !a.isCarryingGas) -1 else 1
        } else if (a.isConstructing != b.isConstructing) {
            if (a.isConstructing) -1 else 1
        } else if (forPosition != null) {
            a.position.getDistance(forPosition).compareTo(b.position.getDistance(forPosition))
        } else 0
    })
}

class ShouldReturnResource : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        if (!unit.isGatheringMinerals && unit.isCarryingMinerals
                || !unit.isGatheringGas && unit.isCarryingGas) return Status.SUCCEEDED
        return Status.FAILED
    }

    override fun start() {
    }
}

class ReturnResource : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        if (!unit.isCarryingGas && !unit.isCarryingMinerals) return Status.SUCCEEDED
        if (!unit.isGatheringMinerals && !unit.isGatheringGas) {
            if (!unit.returnCargo()) return Status.FAILED
            return Status.RUNNING
        }
        return Status.SUCCEEDED
    }
}

class GatherMinerals : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        val targetResource = board().targetResource

        if (targetResource?.isMineralField == false) return Status.FAILED

        if (!unit.isGatheringMinerals || targetResource != null) {
            val target = targetResource ?: FUnit.minerals()
                    .filter { it.distanceTo(unit) < 300 }
                    .minBy { it.distanceTo(unit) }
            board().targetResource = null
            if (target == null || !unit.gather(target)) {
                return Status.FAILED
            }
        }
        return Status.RUNNING
    }
}

class GatherGas : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        val targetResource = board().targetResource

        if (targetResource?.isRefinery == false) return Status.FAILED

        if (!unit.isGatheringGas || targetResource != null) {
            val target = targetResource ?: FUnit.allUnits()
                    .filter { it.isRefinery && it.isPlayerOwned && it.distanceTo(unit) < 300 }
                    .minBy { it.distanceTo(unit) }
            board().targetResource = null
            if (target == null || !unit.gather(target)) {
                return Status.FAILED
            }
        }
        return Status.RUNNING
    }
}

class ShouldConstruct : UnitLT() {
    override fun start() {
    }

    override fun execute(): Status = if (board().construction != null) Status.SUCCEEDED else Status.FAILED
}

class SelectConstructionSiteAsTarget : UnitLT() {
    override fun execute(): Status {
        val construction = board().construction!!
        board().moveTarget = construction.position.toPosition().translated(TilePosition.SIZE_IN_PIXELS, TilePosition.SIZE_IN_PIXELS)
        return Status.SUCCEEDED
    }
}

class AbortConstruct : UnitLT() {
    override fun execute(): Status {
        board().construction = null
        if (board().unit.isConstructing) board().unit.stopConstruct()
        return Status.SUCCEEDED
    }
}

class Construct : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        val construct = board().construction ?: throw IllegalStateException()
        val position = construct.position

        if (construct.started && !construct.commissioned) {
            throw IllegalStateException("Building ${construct.type}was started but not yet 'started' by this worker")
        }

        if (construct.building?.isCompleted ?: false) {
            board().construction = null
            return Status.SUCCEEDED
        }
        if (!unit.isConstructing || unit.isConstructing && unit.buildType != construct.type) {
            if (construct.building != null) {
                if (unit.rightClick(construct.building!!)) {
                    LOG.info("${unit} will continue construction of ${construct.type} at ${construct.position}")
                } else return Status.FAILED
            } else if (unit.construct(construct.type, position)) {
                LOG.info("${unit} sent to construct ${construct.type} at ${construct.position}")
                construct.commissioned = true
            } else {
                return Status.FAILED
            }
        }
        return Status.RUNNING
    }
}

class ShouldDefendWithWorker : UnitLT() {
    override fun start() {}

    override fun execute(): Status {
        val unit = board().unit
        if (FUnit.unitsInRadius(unit.position, 300).filter { it.isPlayerOwned && it.isBase }.any()) return Status.SUCCEEDED
        board().attacking = null
        return Status.FAILED
    }
}

const val RESOURCE_RANGE = 300

class AssignWorkersToResources : LeafTask<Unit>() {
    override fun execute(): Status {
        val myBases = FUnit.allUnits().filter { it.isPlayerOwned && it.isBase }

        myBases.forEach { base ->
            val relevantUnits = FUnit.unitsInRadius(base.position, RESOURCE_RANGE)
            val refineries = relevantUnits.filter { it.isRefinery && it.isPlayerOwned }
            val workers = relevantUnits.filter { it.isWorker && it.isPlayerOwned }.toMutableList()
            val minerals = relevantUnits.filter { it.isMineralField }.toMutableList()

            val gasMissing = refineries.size * 3 - workers.count { it.isGatheringGas }
            if (gasMissing > 0) {
                val refinery = refineries.first()
                repeat(gasMissing) {
                    val worker = workers.firstOrNull { it.isIdle || it.isGatheringMinerals && !it.isCarryingMinerals }
                            ?: workers.firstOrNull { it.isGatheringMinerals } ?: return@repeat
                    worker.board.targetResource = refinery
                    workers.remove(worker)
                }
            } else if (gasMissing < 0) {
                repeat(-gasMissing) {
                    val worker = workers.firstOrNull { it.isGatheringGas && !it.isCarryingGas }
                            ?: workers.firstOrNull { it.isGatheringGas } ?: return@repeat
                    val targetMineral = (minerals.filter { !it.isBeingGathered }.minBy { it.distanceTo(worker) }
                            ?: minerals.minBy { it.distanceTo(worker) })
                    if (targetMineral != null) {
                        worker.board.targetResource = targetMineral
                        minerals.remove(targetMineral)
                        workers.remove(worker)
                    }
                }
            }
            workers.filter { it.isIdle }
                    .forEach { worker ->
                        val targetMineral = (minerals.filter { !it.isBeingGathered }.minBy { it.distanceTo(worker) }
                                ?: minerals.minBy { it.distanceTo(worker) })
                        if (targetMineral != null) {
                            worker.board.targetResource = targetMineral
                            minerals.remove(targetMineral)
                        }
                    }
        }

        return Status.SUCCEEDED
    }

    override fun copyTo(task: Task<Unit>): Task<Unit> = task
}