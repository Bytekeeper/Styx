package org.styx.macro

import bwapi.TilePosition
import bwapi.UnitType
import org.bk.ass.bt.*
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
    val costLock = costLocks.unitCostLock(type)

    init {
        require(type.isBuilding)
    }
}

class CancelBuild(private val board: BuildBoard) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Selector(
            Condition { board.building?.exists != true },
            NodeStatus.RUNNING.after {
                board.building?.cancelBuild()
            }
    )
}

class FindBuildSpot(private val board: BuildBoard) : TreeNode() {
    override fun exec() {
        board.provideLocation(board)
        if (board.location == null) {
            buildPlan.plannedUnits += PlannedUnit(board.type, consumedUnit = Styx.self.race.worker)
            running()
        } else
            success()
    }
}

class PrepareBuild(private val board: BuildBoard) : BehaviorTree() {
    private var hysteresisFrames = 0

    override fun getRoot(): TreeNode =
            Selector(
                    LambdaNode(this::checkForExistingBuilding),
                    Parallel(Parallel.Policy.SEQUENCE,
                            EnsureDependenciesFor(board.type),
                            Sequence(
                                    FindBuildSpot(board),
                                    LambdaNode {
                                        board.workerLock.acquire()
                                        val buildPosition = board.location!!.toPosition() + board.type.dimensions / 2
                                        val worker = board.workerLock.item
                                        board.framesBeforeStartable = worker?.framesToTravelTo(buildPosition) ?: 0
                                        board.costLock.setFutureFrames(board.framesBeforeStartable + hysteresisFrames)
                                        board.costLock.acquire()
                                        if (worker != null && board.costLock.isSatisfiedLater) {
                                            hysteresisFrames = Config.productionHysteresisFrames
                                            if (!board.costLock.isSatisfied) {
                                                buildPlan.plannedUnits += PlannedUnit(board.type, board.framesBeforeStartable, consumedUnit = Styx.self.race.worker)
                                                BasicActions.move(worker, buildPosition)
                                                NodeStatus.RUNNING
                                            } else
                                                NodeStatus.SUCCESS
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
            if (board.workerLock.item != null && candidate?.tilePosition == targetLocation && candidate.unitType == board.type) {
                board.building = candidate
                return NodeStatus.SUCCESS
            } else // Maybe destroyed/Cancelled?
                board.building = null
            if (board.workerLock.item?.canBuildHere(targetLocation, board.type) == false || candidate?.tilePosition == targetLocation && candidate.unitType.isBuilding)
                board.location = null
        }
        return NodeStatus.FAILURE
    }

    override fun reset() {
        super.reset()
        board.workerLock.reset()
        board.location = null
        board.building = null
        hysteresisFrames = 0
    }

}

class OrderBuild(private val board: BuildBoard) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Selector(
                    Condition { board.building?.exists == true },
                    LambdaNode(this::orderBuild)
            )

    private fun orderBuild(): NodeStatus {
        val worker = board.workerLock.item ?: error("Worker must be locked")
        when {
            board.costLock.isSatisfied -> {
                if (!board.type.isResourceDepot && !board.type.isResourceContainer && units.my(board.type).isNotEmpty()) {
                    println("Building a second ${board.type} for whatever reasons!")
                }
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

class StartBuild(private val board: BuildBoard) : BehaviorTree() {
    constructor(type: UnitType) : this(BuildBoard(type))

    override fun getRoot() =
            Sequence(
                    PrepareBuild(board),
                    OrderBuild(board)
            )

    override fun toString(): String = "Start building $board.type at ${board.location}"
}

class Build(private val board: BuildBoard) : BehaviorTree() {
    constructor(type: UnitType) : this(BuildBoard(type))

    override fun getRoot() = Memo(
            Selector(
                    Condition { board.building?.isCompleted == true },
                    Sequence(
                            StartBuild(board),
                            Running
                    )
            )
    )
}