package org.styx.macro

import bwapi.TilePosition
import bwapi.UnitType
import org.styx.*
import org.styx.Styx.buildPlan
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.action.BasicActions
import org.styx.global.PlannedUnit


data class BuildBoard(
        val type: UnitType,
        val provideLocation: (BuildBoard) -> Unit = {
            it.location = it.location ?: ConstructionPosition.findPositionFor(type)
        },
        var location: TilePosition? = null,
        var building: SUnit? = null,
        var framesBeforeStartable: Int = 0
) {
    val workerLock: UnitLock = UnitLock({ it.unitType.isWorker && resources.availableUnits.contains(it) }) {
        val eval = closeTo(location!!.toPosition())
        resources.availableUnits.filter { it.unitType.isWorker }.minBy { eval(it) }
    }
    val costLock: UnitCostLock = UnitCostLock(type)

    init {
        require(type.isBuilding)
    }
}

class CancelBuild(private val board: BuildBoard) : BehaviorTree("Cancelling Build ${board.type}") {
    override fun buildRoot(): SimpleNode = Sel("Cancelling",
            Condition { board.building?.exists != true },
            NodeStatus.RUNNING.after {
                board.building?.cancelBuild()
            }
    )
}

class FindBuildSpot(private val board: BuildBoard) : BTNode() {
    override fun tick(): NodeStatus {
        board.provideLocation(board)
        return if (board.location == null) {
            buildPlan.plannedUnits += PlannedUnit(board.type, consumedUnit = Styx.self.race.worker)
            NodeStatus.RUNNING
        } else
            NodeStatus.DONE
    }
}

class PrepareBuild(private val board: BuildBoard) : BehaviorTree("Preparing Build ${board.type}") {
    private var hysteresisFrames = 0

    override fun buildRoot(): SimpleNode =
            Sel("Prepare if not started",
                    this::checkForExistingBuilding,
                    Par("Ensure money and dependencies available", false,
                            EnsureDependenciesFor(board.type),
                            Seq("Find place to build and lock resources",
                                    FindBuildSpot(board),
                                    {
                                        board.workerLock.acquire()
                                        val buildPosition = board.location!!.toPosition() + board.type.dimensions / 2
                                        val worker = board.workerLock.unit
                                        board.framesBeforeStartable = worker?.framesToTravelTo(buildPosition) ?: 0
                                        board.costLock.futureFrames = board.framesBeforeStartable + hysteresisFrames
                                        board.costLock.acquire()
                                        if (worker != null && board.costLock.willBeSatisfied) {
                                            hysteresisFrames = Config.productionHysteresisFrames
                                            if (!board.costLock.satisfied) {
                                                buildPlan.plannedUnits += PlannedUnit(board.type, board.framesBeforeStartable, consumedUnit = Styx.self.race.worker)
                                                BasicActions.move(worker, buildPosition)
                                                NodeStatus.RUNNING
                                            } else
                                                NodeStatus.DONE
                                        } else {
                                            hysteresisFrames = 0
                                            buildPlan.plannedUnits += PlannedUnit(board.type, consumedUnit = Styx.self.race.worker)
                                            board.workerLock.release()
                                            NodeStatus.RUNNING
                                        }
                                    }
                            )
                    )
            )

    private fun checkForExistingBuilding(): NodeStatus {
        board.location?.let { targetLocation ->
            val targetPos = targetLocation.toPosition() + board.type.dimensions / 2
            val candidate = units.mine.nearest(targetPos.x, targetPos.y)
            if (board.workerLock.unit != null && candidate?.tilePosition == targetLocation && candidate.unitType == board.type) {
                board.building = candidate
                return NodeStatus.DONE
            } else // Maybe destroyed/Cancelled?
                board.building = null
            if (board.workerLock.unit?.canBuildHere(targetLocation, board.type) == false || candidate?.tilePosition == targetLocation && candidate.unitType.isBuilding)
                board.location = null
        }
        return NodeStatus.FAILED
    }

    override fun reset() {
        super.reset()
        board.workerLock.reset()
        board.location = null
        board.building = null
        hysteresisFrames = 0
    }

}

class OrderBuild(private val board: BuildBoard) : BehaviorTree("Order build ${board.type}") {
    override fun buildRoot(): SimpleNode = Sel("Start Build",
            Condition { board.building?.exists == true },
            this::orderBuild
    )

    private fun orderBuild(): NodeStatus {
        val worker = board.workerLock.unit ?: error("Worker must be locked")
        when {
            board.costLock.satisfied -> {
                buildPlan.plannedUnits += PlannedUnit(board.type, 0, consumedUnit = Styx.self.race.worker)
                BasicActions.build(worker, board.type, board.location!!)
            }
            else -> {
                buildPlan.plannedUnits += PlannedUnit(board.type, consumedUnit = Styx.self.race.worker)
                board.workerLock.release()
            }
        }
        return NodeStatus.RUNNING
    }
}

class StartBuild(private val board: BuildBoard) : BehaviorTree("Start Build ${board.type}") {
    constructor(type: UnitType) : this(BuildBoard(type))

    override fun buildRoot(): SimpleNode = Seq("Execute Build",
            PrepareBuild(board),
            OrderBuild(board)
    )

    override fun toString(): String = "Start building $board.type at ${board.location}"
}

class Build(private val board: BuildBoard) : BehaviorTree("Build ${board.type}") {
    constructor(type: UnitType) : this(BuildBoard(type))

    override fun buildRoot(): SimpleNode = Memo(
            Sel("Execute",
                    Condition { board.building?.isCompleted == true },
                    Seq("Execute",
                            StartBuild(board),
                            NodeStatus.RUNNING
                    )
            )
    )
}