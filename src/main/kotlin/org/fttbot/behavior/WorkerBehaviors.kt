package org.fttbot.behavior

import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.board
import org.fttbot.layer.UnitQuery
import org.fttbot.layer.board
import org.fttbot.layer.isMyUnit
import org.fttbot.translated
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit

fun findWorker(forPosition: Position? = null, maxRange: Double = 400.0): Worker<*>? {
    var candidates = UnitQuery.myWorkers
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
        val unit = board().unit as Worker<*>
        if (!unit.isGatheringMinerals && unit.isCarryingMinerals
                || !unit.isGatheringGas && unit.isCarryingGas) return Status.SUCCEEDED
        return Status.FAILED
    }

    override fun start() {
    }
}

class ReturnResource : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit as Worker<*>
        if (!unit.isCarryingGas && !unit.isCarryingMinerals) return Status.SUCCEEDED
        if (!unit.isGatheringMinerals && !unit.isGatheringGas) {
            if (!latencyGuarded { unit.returnCargo() }) return Status.FAILED
            return Status.RUNNING
        }
        return Status.SUCCEEDED
    }
}

class GatherMinerals : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit as Worker<*>
        val targetResource = board().targetResource

        if (targetResource != null && targetResource !is MineralPatch) return Status.FAILED

        if (!unit.isGatheringMinerals || targetResource != null) {
            val target = (targetResource ?: UnitQuery.minerals
                    .filter { it.getDistance(unit) < 300 }
                    .minBy { it.getDistance(unit) }) as? MineralPatch
            board().targetResource = null
            if (target == null || !latencyGuarded { unit.gather(target) }) {
                return Status.FAILED
            }
        }
        return Status.RUNNING
    }
}

class GatherGas : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit as Worker<GasMiningFacility>
        val targetResource = board().targetResource

        if (targetResource != null && targetResource !is GasMiningFacility) return Status.FAILED

        if (!unit.isGatheringGas || targetResource != null) {
            val target = (targetResource ?: UnitQuery.allUnits()
                    .filter { it is GasMiningFacility && it.isMyUnit && it.getDistance(unit) < 300 }
                    .minBy { it.getDistance(unit) }) as? GasMiningFacility
            board().targetResource = null
            if (target == null || !latencyGuarded { unit.gather(target) }) {
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
        val unit = board().unit as SCV
        board().construction = null
        if (unit.isConstructing) unit.haltConstruction()
        return Status.SUCCEEDED
    }
}

class Construct : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit as Worker<*>
        val construct = board().construction ?: throw IllegalStateException()
        val position = construct.position

        if (construct.started && !construct.commissioned) {
            throw IllegalStateException("Building ${construct.type}was started but not yet 'started' by this worker")
        }

        if (construct.building?.isCompleted == true) {
            board().construction = null
            return Status.SUCCEEDED
        }

        if (latencyAffected) return Status.RUNNING

        if (!unit.isConstructing || unit.isConstructing && unit.buildType != construct.type) {
            if (unit.isConstructing && unit.buildType != construct.type) {
                (unit as SCV).haltConstruction()
            }
            if (construct.building != null) {
                if (latencyGuarded { unit.rightClick(construct.building!!, false) }) {
                    LOG.info("${unit} will continue construction of ${construct.type} at ${construct.position}")
                } else return Status.FAILED
            } else if (latencyGuarded { unit.build(position, construct.type) }) {
                LOG.info("${unit} at ${unit.position} sent to construct ${construct.type} at ${construct.position}")
                construct.commissioned = true
            } else {
                LOG.severe("$unit at ${unit.position} failed to construct ${construct.type} at ${construct.position}")
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
        if (UnitQuery.unitsInRadius(unit.position, 300).filter { it is Base && it is PlayerUnit && it.isMyUnit }.any()) return Status.SUCCEEDED
        board().attacking = null
        return Status.FAILED
    }
}

const val RESOURCE_RANGE = 300

class AssignWorkersToResources : LeafTask<Unit>() {
    override fun execute(): Status {
        val myBases = UnitQuery.ownedUnits.filter { it.isMyUnit && it is Base }

        myBases.forEach { base ->
            val relevantUnits = UnitQuery.unitsInRadius(base.position, RESOURCE_RANGE)
            val refineries = relevantUnits.filter { it is GasMiningFacility && it.isMyUnit && it.isCompleted }.map { it as GasMiningFacility }
            val workers = relevantUnits.filter { it is Worker<*> && it.isMyUnit && it.isCompleted }.map { it as Worker<*> }.toMutableList()
            val minerals = relevantUnits.filter { it is MineralPatch }.map { it as MineralPatch }.toMutableList()

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
                    val targetMineral = (minerals.filter { !it.isBeingGathered }.minBy { it.getDistance(worker) }
                            ?: minerals.minBy { it.getDistance(worker) })
                    if (targetMineral != null) {
                        worker.board.targetResource = targetMineral
                        minerals.remove(targetMineral)
                        workers.remove(worker)
                    }
                }
            }
            workers.filter { it.isIdle }
                    .forEach { worker ->
                        val targetMineral = (minerals.filter { !it.isBeingGathered }.minBy { it.getDistance(worker) }
                                ?: minerals.minBy { it.getDistance(worker) })
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