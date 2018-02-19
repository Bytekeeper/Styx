package org.fttbot.behavior

import org.fttbot.FTTBot
import org.fttbot.NodeStatus
import org.fttbot.info.UnitQuery
import org.fttbot.info.board
import org.fttbot.info.isMyUnit
import org.fttbot.translated
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.unit.*

fun findWorker(forPosition: Position? = null, maxRange: Double = 800.0, candidates: List<Worker> = UnitQuery.myWorkers): Worker? {
    val selection =
            if (forPosition != null) {
                candidates.filter { it.getDistance(forPosition) <= maxRange }
            } else candidates
    return selection.minWith(Comparator { a, b ->
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
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as Worker
        if (!unit.isGatheringMinerals && unit.isCarryingMinerals
                || !unit.isGatheringGas && unit.isCarryingGas) return NodeStatus.SUCCEEDED
        return NodeStatus.FAILED
    }
}

class SelectBaseAsTarget : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as MobileUnit
        val targetBase = UnitQuery.myBases.minBy { it.getDistance(unit) }
        if (targetBase == null) return NodeStatus.FAILED
        unit.board.moveTarget = targetBase.position
        return NodeStatus.SUCCEEDED
    }
}

class ReturnResource : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as Worker
        if (!unit.isCarryingGas && !unit.isCarryingMinerals) return NodeStatus.SUCCEEDED
        if (!unit.isGatheringMinerals && !unit.isGatheringGas) {
            if (!unit.returnCargo()) return NodeStatus.FAILED
            return NodeStatus.RUNNING
        }
        return NodeStatus.SUCCEEDED
    }
}

class GatherMinerals : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as Worker
        val gathering = board.goal as Gathering
        val targetResource = gathering.target

        if (targetResource != null && targetResource !is MineralPatch) return NodeStatus.FAILED

        if (unit.isInterruptible && (!unit.isGatheringMinerals || targetResource != null)) {
            val target = (targetResource as? MineralPatch ?: UnitQuery.minerals
                    .filter { it.getDistance(unit) < 300 }
                    .minBy { it.getDistance(unit) })
                    ?: UnitQuery.minerals.filter { m -> UnitQuery.myBases.any { it.getDistance(m) < 300 } }
                            .minBy { it.getDistance(unit) }
            gathering.target = null
            if (target == null || !unit.gather(target)) {
                return NodeStatus.FAILED
            }
        }
        return NodeStatus.RUNNING
    }
}

class GatherGas : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as Worker
        val gathering = board.goal as Gathering
        val targetResource = gathering.target

        if (targetResource != null && targetResource !is GasMiningFacility) return NodeStatus.FAILED

        if (unit.isInterruptible && (!unit.isGatheringGas || targetResource != null)) {
            val target = (targetResource ?: UnitQuery.allUnits()
                    .filter { it is GasMiningFacility && it.isMyUnit && it.getDistance(unit) < 300 }
                    .minBy { it.getDistance(unit) }) as? GasMiningFacility
            gathering.target = null
            if (target == null || !unit.gather(target)) {
                return NodeStatus.FAILED
            }
        }
        return NodeStatus.RUNNING
    }
}

class Repair : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val repairing = board.goal as Repairing
        val unit = board.unit as SCV
        if (unit.isRepairing) return NodeStatus.RUNNING
        if (unit.targetUnit == repairing.target || unit.repair(repairing.target)) {
            return NodeStatus.RUNNING
        }
        return NodeStatus.FAILED
    }

}

class SelectConstructionSiteAsTarget : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val construction = board.goal as Construction
        board.moveTarget = construction.position.toPosition().translated(TilePosition.SIZE_IN_PIXELS, TilePosition.SIZE_IN_PIXELS)
        return NodeStatus.SUCCEEDED
    }
}

class AbortConstruct : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as SCV
        if (board.goal !is Construction && board.goal !is ResumeConstruction) throw IllegalStateException()
        board.goal = null
        if (unit.isConstructing) unit.haltConstruction()
        return NodeStatus.SUCCEEDED
    }
}

object ResumeConstruct : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as SCV
        val resume = board.goal as ResumeConstruction

        if (!resume.building.exists()) return NodeStatus.FAILED
        if (resume.building.isCompleted) return NodeStatus.SUCCEEDED

        if (unit.targetUnit != resume.building) {
            if (unit.rightClick(resume.building, false)) {
                LOG.info("${unit} will continue construction of ${resume.building} at ${resume.building.position}")
            } else return NodeStatus.FAILED
        }
        return NodeStatus.RUNNING
    }

}

class Construct : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as Worker
        val construct = board.goal as Construction
        val position = construct.position

        if (construct.started && !construct.commissioned) {
            LOG.severe("Building ${construct.type} was started but not yet 'started' by this worker")
            return NodeStatus.FAILED
        }

        if (construct.building?.isCompleted == true) {
            board.goal = null
            return NodeStatus.SUCCEEDED
        }

        if (!unit.isConstructing || unit.isConstructing && unit.buildType != construct.type) {
            if (unit.isConstructing && unit.buildType != construct.type) {
                (unit as SCV).haltConstruction()
            }
            if (construct.building != null) {
                LOG.severe("${unit}: Huh, building to 'build' is already being constructed!")
                return NodeStatus.FAILED
            } else if (unit.build(position, construct.type)) {
                LOG.info("${unit} at ${unit.position} sent to construct ${construct.type} at ${construct.position}")
                construct.commissioned = true
            } else {
                LOG.severe("$unit at ${unit.position} failed to construct ${construct.type} at ${construct.position}")
                return NodeStatus.FAILED
            }
        }
        return NodeStatus.RUNNING
    }
}