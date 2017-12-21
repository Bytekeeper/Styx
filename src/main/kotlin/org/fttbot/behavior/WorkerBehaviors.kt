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

    override fun cpy(): Task<BBUnit> = ReturnResource()
}

class GatherMinerals : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        val targetResource = board().targetResource

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

    override fun cpy(): Task<BBUnit> = GatherMinerals()
}

class GatherGas : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        val targetResource = board().targetResource

        if (!unit.isGatheringMinerals || targetResource != null) {
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

    override fun cpy(): Task<BBUnit> = GatherGas()
}

class ShouldConstruct : UnitLT() {
    override fun start() {
    }

    override fun execute(): Status = if (board().construction != null) Status.SUCCEEDED else Status.FAILED

    override fun cpy(): Task<BBUnit> = ShouldConstruct()
}

class SelectConstructionSiteAsTarget : UnitLT() {
    override fun execute(): Status {
        val construction = board().construction!!
        board().moveTarget = construction.position.toPosition().translated(TilePosition.SIZE_IN_PIXELS / 2, TilePosition.SIZE_IN_PIXELS / 2)
        return Status.SUCCEEDED
    }

    override fun cpy(): Task<BBUnit> = SelectConstructionSiteAsTarget()
}

class Construct : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        val construct = board().construction ?: throw IllegalStateException()
        val position = construct.position

        if (construct.started && !construct.commissioned) {
            throw IllegalStateException("Building ${construct.type}was started but not yet 'started' by this worker")
        }

        if (!unit.isConstructing && construct.started) {
            board().construction = null
            return Status.SUCCEEDED
        }
        if (!unit.isConstructing || unit.isConstructing && unit.buildType != construct.type) {
            if (unit.construct(construct.type, position)) {
                construct.commissioned = true
            } else {
                return Status.FAILED
            }
        }
        return Status.RUNNING
    }

    override fun cpy(): Task<BBUnit> = Construct()
}

class ShouldDefendWithWorker : UnitLT() {
    override fun start() {}

    override fun execute(): Status {
        return Status.FAILED
    }

    override fun cpy(): Task<BBUnit> = ShouldDefendWithWorker()
}

const val RESOURCE_RANGE = 300

class AssignWorkersToResources : LeafTask<Unit>() {
    override fun execute(): Status {
        val myBases = FUnit.allUnits().filter { it.isPlayerOwned && it.isBase }

        val baseToWorker = FUnit.myWorkers()
                .filter { it.isIdle }
                .groupByTo(HashMap(), { it.closest(myBases) })
        baseToWorker.forEach { base, workers ->
            if (base != null) {
                workers.sortBy { it.distanceTo(base) }
            }
        }
        val baseToMineralField = FUnit.minerals()
                .groupByTo(HashMap(), { it.closest(myBases) })
        baseToMineralField.forEach { base, minerals ->
            if (base != null) {
                minerals.removeAll { it.distanceTo(base) >= RESOURCE_RANGE }
                minerals.sortBy { it.distanceTo(base) }
            }
        }
        val baseToRefinery = FUnit.allUnits()
                .filter { it.isRefinery && it.isPlayerOwned }
                .groupBy { it.closest(myBases) }

        baseToRefinery.forEach { base, refineries ->
            val workers = baseToWorker[base]
            if (workers != null && base != null) {
                refineries.filter { it.distanceTo(base) < RESOURCE_RANGE }
                        .forEach { ref ->
                            for (i in 1..3) {
                                if (!workers.isEmpty())
                                    workers.removeAt(0).board.targetResource = ref
                            }
                        }
            }
        }
        baseToMineralField.forEach { base, minerals ->
            val workers = baseToWorker[base]
            if (workers != null && base != null) {
                minerals.filter { it.distanceTo(base) < RESOURCE_RANGE }
                        .forEach { mineral ->
                            if (!workers.isEmpty()) {
                                workers.removeAt(0).board.targetResource = mineral
                            }
                        }
            }
        }
        return Status.SUCCEEDED
    }

    override fun copyTo(task: Task<Unit>?): Task<Unit> = AssignWorkersToResources()
}