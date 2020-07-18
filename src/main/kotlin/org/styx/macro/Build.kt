package org.styx.macro

import bwapi.TilePosition
import bwapi.UnitType
import org.bk.ass.bt.*
import org.bk.ass.manage.GMS
import org.bk.ass.manage.Lock
import org.styx.*
import org.styx.Styx.buildPlan
import org.styx.Styx.frame
import org.styx.Styx.units
import org.styx.action.BasicActions
import org.styx.global.PlannedUnit
import org.styx.micro.AvoidCombat


data class BuildBoard(
        val type: UnitType,
        val provideLocation: () -> TilePosition? = { ConstructionPosition.findPositionFor(type) },
        var building: SUnit? = null,
        var framesBeforeStartable: Int? = null
) {
    val workerLock: UnitLock = UnitLock({ it.unitType.isWorker && UnitReservation.isAvailable(it) }) {
        val eval = closeTo(positionLock.item.toPosition())
        UnitReservation.availableItems.filter { it.unitType.isWorker }.minBy { eval(it) }
    }
    val costLock: Lock<GMS> = costLocks.unitCostLock(type)
    val positionLock = TileLock({ workerLock.item?.canBuildHere(it, type) != false && (costLock.isSatisfiedLater || frame % 137 != 0) }, provideLocation)
    var initalEstimatedTravelFrames: Int? = null
    var moveWorkerFrame: Int? = null

    init {
        require(type.isBuilding)
        costLock.setHysteresisFrames(Config.productionHysteresisFrames)
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

class MarkUnitPlanned(private val board: BuildBoard) : TreeNode() {
    override fun init() {
        running()
    }

    override fun reset() {
        running()
    }

    override fun exec() {
        buildPlan.plannedUnits += PlannedUnit(board.type, framesToStart = board.framesBeforeStartable, consumedUnit = Styx.self.race.worker)
        running()
    }

}

class PrepareBuild(private val board: BuildBoard) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Selector(
                    LambdaNode(this::checkForExistingBuilding),
                    Parallel(
                            GetStuffToTrainOrBuild(board.type),
                            Sequence(
                                    Selector(
                                            AcquireLock(board.positionLock),
                                            MarkUnitPlanned(board)
                                    ),
                                    Succeeder(AcquireLock(board.workerLock)),
                                    Selector(
                                            LambdaNode {
                                                val buildPosition = board.positionLock.item.toPosition() + board.type.center
                                                val worker = board.workerLock.item
                                                board.framesBeforeStartable = worker?.framesToTravelTo(buildPosition)
                                                board.initalEstimatedTravelFrames = board.initalEstimatedTravelFrames
                                                        ?: board.framesBeforeStartable
                                                board.costLock.setFutureFrames(board.framesBeforeStartable ?: 0)
                                                board.costLock.acquire()
                                                if (worker != null && board.costLock.isSatisfiedLater) {
                                                    if (worker.unitType == UnitType.Zerg_Drone) {
                                                        // Add one supply for the lost worker
                                                        ResourceReservation.release(null, GMS(0, 0, UnitType.Zerg_Drone.supplyRequired()))
                                                    }
                                                    NodeStatus.SUCCESS
                                                } else {
                                                    NodeStatus.FAILURE
                                                }
                                            },
                                            Sequence(ReleaseLock(board.workerLock), MarkUnitPlanned(board))
                                    )
                            )
                    )
            )

    private fun checkForExistingBuilding(): NodeStatus {
        if (board.positionLock.isSatisfied) {
            val targetLocation = board.positionLock.item
            val targetPos = targetLocation.toPosition() + board.type.center
            val candidate = units.mine.nearest(targetPos.x, targetPos.y, 128) { it.unitType == board.type}
            if (board.workerLock.item != null && candidate?.tilePosition == targetLocation && candidate.unitType == board.type) {
                board.building = candidate
                return NodeStatus.SUCCESS
            }
            board.building = null
        }
        return NodeStatus.FAILURE
    }

    override fun reset() {
        super.reset()
        board.building = null
    }

}

class OrderBuild(private val board: BuildBoard) : TreeNode() {
    private val avoidCombat = AvoidCombat { board.workerLock.item }
    private val markUnitPlanned = MarkUnitPlanned(board)
    override fun exec(executionContext: ExecutionContext) {
        if (board.building?.exists == true) {
            success()
            return
        }
        val worker = board.workerLock.item ?: error("Worker must be locked")
        avoidCombat.exec()
        markUnitPlanned.exec()
        require(board.type.whatBuilds().first == worker.unitType) { "$worker cannot build a ${board.type}" }
        if (avoidCombat.status == NodeStatus.SUCCESS) {
            when {
                board.costLock.isSatisfied -> {
                    if (!board.type.isResourceDepot && !board.type.isResourceContainer && units.my(board.type).isNotEmpty()) {
//                        println("Building a second ${board.type} for whatever reasons!")
                    }
                    if (worker.unitType == UnitType.Zerg_Drone) {
                        // Add one supply for the lost worker
                        ResourceReservation.release(null, GMS(0, 0, UnitType.Zerg_Drone.supplyRequired()))
                    }
                    BasicActions.build(worker, board.type, board.positionLock.item)
                }
                board.costLock.isSatisfiedLater -> {
                    board.moveWorkerFrame = board.moveWorkerFrame ?: Styx.frame
                    BasicActions.move(worker, board.positionLock.item.toPosition() + board.type.center)
                }
                else -> {
                    board.workerLock.release()
                }
            }
        }
        running()
    }

    override fun exec() {
    }
}

class StartBuild(private val board: BuildBoard) : BehaviorTree() {
    constructor(type: UnitType) : this(BuildBoard(type))

    override fun getRoot() =
            Sequence(
                    PrepareBuild(board),
                    OrderBuild(board)
            )

    override fun toString(): String = "Start building $board.type at ${board.positionLock.item}"
}

class Build(private val board: BuildBoard) : BehaviorTree() {
    constructor(type: UnitType) : this(BuildBoard(type))

    override fun getRoot() = Memo(
            Selector(
                    Condition { board.building?.isCompleted == true },
                    Sequence(
                            StartBuild(board),
                            Wait
                    )
            )
    )
}