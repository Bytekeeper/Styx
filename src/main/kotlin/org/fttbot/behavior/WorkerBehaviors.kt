package org.fttbot.behavior

import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.ConstructionPosition
import org.fttbot.layer.FUnit

class GatherMinerals : LeafTask<BBUnit>() {
    override fun execute(): Status {
        val unit = `object`.unit
        val order = `object`.order as? Gathering
        if (unit.isCarryingGas) {
            unit.returnCargo()
        } else if (!unit.isGatheringMinerals || order?.target != null) {
            val target = order?.target ?: FUnit.minerals()
                    .filter { it.distanceTo(unit) < 300 }
                    .minBy { it.distanceTo(unit) }
            order?.target = null
            if (target != null) {
                unit.gather(target)
            } else {
                return Status.FAILED
            }
        }
        return Status.RUNNING
    }

    override fun copyTo(task: Task<BBUnit>): Task<BBUnit> = GatherMinerals()
}

class GatherGas : LeafTask<BBUnit>() {
    override fun execute(): Status {
        val unit = `object`.unit
        val order = `object`.order as? Gathering
        if (unit.isCarryingMinerals) {
            unit.returnCargo()
        } else if (!unit.isGatheringMinerals || order?.target != null) {
            val target = order?.target ?: FUnit.allUnits()
                    .filter { it.isRefinery && it.isPlayerOwned && it.distanceTo(unit) < 300 }
                    .minBy { it.distanceTo(unit) }
            order?.target = null
            if (target != null) {
                unit.gather(target)
            } else {
                return Status.FAILED
            }
        }
        return Status.RUNNING
    }

    override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = GatherGas()
}

class Construct : LeafTask<BBUnit>() {
    var moveOrdered = false

    init {
        guard = Guard()
    }

    override fun start() {
        val unit = `object`.unit
        moveOrdered = false
        val construct = `object`.order as Construction
        if (construct.position == null) {
            construct.position = ConstructionPosition.findPositionFor(construct.type)
        }
    }

    override fun execute(): Status {
        val unit = `object`.unit
        val construct = `object`.order as Construction
        val position = construct.position ?: return Status.FAILED

        if (!unit.isConstructing && construct.started) {
            `object`.order = Order.NONE
            return Status.SUCCEEDED
        }
        if ((unit.isCarryingGas || unit.isCarryingMinerals) && unit.returnCargo()) {
            return Status.RUNNING
        }
        if (!moveOrdered) {
            if (!unit.move(position)) {
                return Status.RUNNING
            }
            moveOrdered = true
        }
        if (!unit.isConstructing || unit.isConstructing && unit.buildType != construct.type) {
            if (unit.distanceTo(position) <= 8) {
                unit.construct(construct.type, position)
            }
        }
        return Status.RUNNING
    }

    override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Construct()

    class Guard : LeafTask<BBUnit>() {
        override fun execute(): Status = if (`object`.order is Construction) Status.SUCCEEDED else Status.FAILED

        override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Guard()
    }
}

const val RESOURCE_RANGE = 300

class AssignWorkersToResources : LeafTask<Unit>() {
    override fun execute(): Status {
        val myBases = FUnit.allUnits().filter { it.isPlayerOwned && it.isBase }

        val baseToWorker = FUnit.myWorkers()
                .filter { it.isIdle && BBUnit.of(it).order == Order.NONE }
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
                                    BBUnit.of(workers.removeAt(0)).order = Gathering(ref)
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
                                BBUnit.of(workers.removeAt(0)).order = Gathering(mineral)
                            }
                        }
            }
        }
        return Status.SUCCEEDED
    }

    override fun copyTo(task: Task<Unit>?): Task<Unit> = AssignWorkersToResources()
}