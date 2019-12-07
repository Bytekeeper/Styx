package org.styx.macro

import bwapi.Position
import bwapi.TilePosition
import bwapi.UnitType
import org.styx.*
import org.styx.Styx.buildPlan
import org.styx.Styx.diag
import org.styx.Styx.economy
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.action.BasicActions

class StartBuild(private val type: UnitType,
                 private val positionFinder: () -> TilePosition? = { ConstructionPosition.findPositionFor(type) }) : MemoLeaf() {
    init {
        require(type.isBuilding)
    }
    private var at: TilePosition? = null
    private val workerLock = UnitLock {
        val eval = closeTo(at!!.toPosition())
        resources.availableUnits.filter { it.unitType.isWorker }.minBy { eval(it) }
    }
    private val costLock = UnitCostLock(type)
    private var hysteresisFrames = 0
    private val dependency = EnsureDependenciesFor(type)
    var building: SUnit? = null
        private set

    private fun checkForExistingBuilding(): NodeStatus {
        at?.let {
            val targetPos = it.toPosition() + type.dimensions / 2
            val candidate = units.mine.nearest(targetPos.x, targetPos.y)
            if (workerLock.unit != null && candidate?.tilePosition == it && candidate.unitType == type) {
                building = candidate
                return NodeStatus.DONE
            } else // Maybe destroyed/Cancelled?
                building = null
            if (workerLock.unit?.canBuildHere(it, type) == false || candidate?.tilePosition == it && candidate.unitType.isBuilding)
                at = null
        }
        return NodeStatus.FAILED
    }

    override fun tick(): NodeStatus {
        val existingBuildingState = checkForExistingBuilding()
        if (existingBuildingState != NodeStatus.FAILED)
            return existingBuildingState
        val dependencyStatus = dependency.perform()
        if (dependencyStatus == NodeStatus.FAILED)
            return NodeStatus.FAILED
        val buildAt = at ?: positionFinder() ?: run {
            buildPlan.plannedUnits += PlannedUnit(type)
            return NodeStatus.RUNNING
        }
        at = buildAt
        workerLock.acquireUnlessFailed { worker ->
            val buildPosition = buildAt.toPosition() + type.dimensions / 2
            val travelFrames = worker?.framesToTravelTo(buildPosition) ?: 0
            costLock.futureFrames = travelFrames + hysteresisFrames
            costLock.acquire()
            if (worker != null)
                orderBuild(worker, buildAt, travelFrames, buildPosition)
            else {
                buildPlan.plannedUnits += PlannedUnit(type)
                NodeStatus.RUNNING
            }
        }
        return NodeStatus.RUNNING
    }

    private fun orderBuild(worker: SUnit, buildAt: TilePosition, travelFrames: Int, buildPosition: Position): NodeStatus =
            if (costLock.satisfied) {
                if (units.my(type).isNotEmpty() && type != UnitType.Zerg_Hatchery && type != UnitType.Zerg_Extractor) {
                    println("!!")
                }

                buildPlan.plannedUnits += PlannedUnit(type, 0)
                hysteresisFrames = 0
                BasicActions.build(worker, type, buildAt)
                NodeStatus.RUNNING
            } else if (costLock.willBeSatisfied) {
                buildPlan.plannedUnits += PlannedUnit(type, travelFrames)
                if (hysteresisFrames == 0 && Config.logEnabled) {
                    diag.traceLog("Build ${type} with ${worker.diag()} at ${buildAt.toWalkPosition().diag()} - GMS: ${resources.availableGMS}, " +
                            "actual resources: ${economy.currentResources}, " +
                            "frames to target: $travelFrames, plan queue: ${buildPlan.plannedUnits}")
                }
                hysteresisFrames--
                BasicActions.move(worker, buildPosition)
                if (costLock.willBeSatisfied) hysteresisFrames = Config.productionHysteresisFrames
                NodeStatus.RUNNING
            } else {
                buildPlan.plannedUnits += PlannedUnit(type)
                if (hysteresisFrames > 0) {
                    diag.traceLog("Postponed build ${type} with ${worker.diag()} at ${buildAt.toWalkPosition().diag()}  - GMS: ${resources.availableGMS}, " +
                            "frames to target: $travelFrames, hysteresis frames: " +
                            "$hysteresisFrames")
                }
                hysteresisFrames = 0
                NodeStatus.FAILED
            }

    override fun reset() {
        super.reset()
        workerLock.reset()
        at = null
        hysteresisFrames = 0
    }

    override fun toString(): String = "Start building $type at $at"
}

class Build(private val type: UnitType,
            positionFinder: () -> TilePosition? = { ConstructionPosition.findPositionFor(type) }) : MemoLeaf() {
    private val startBuild = StartBuild(type, positionFinder)

    var building: SUnit? = null
        private set

    override fun tick(): NodeStatus {
        if (building?.isCompleted == true)
            return NodeStatus.DONE
        val startBuildStatus = startBuild.perform()
        if (startBuildStatus != NodeStatus.DONE)
            return startBuildStatus
        building = startBuild.building
        return NodeStatus.RUNNING
    }

    override fun toString(): String = "Build $type"
}