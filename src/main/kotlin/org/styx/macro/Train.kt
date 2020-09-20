package org.styx.macro

import bwapi.UnitType
import org.bk.ass.bt.*
import org.bk.ass.manage.GMS
import org.bk.ass.manage.Lock
import org.styx.*
import org.styx.Styx.economy
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.action.BasicActions
import org.styx.global.PlannedUnit


class WaitForUnitLock(lock: UnitLock) : Decorator(
        Repeat(Repeat.Policy.SELECTOR, AcquireLock(lock))
)

class WaitForCostLock(lock: Lock<GMS>) : Decorator(
        Repeat(Repeat.Policy.SELECTOR, AcquireLock(lock))
)

data class TrainBoard(
        val type: UnitType,
        var trainee: SUnit? = null
) {
    init {
        require(!type.isBuilding)
    }

    val trainerLock = UnitLock({ it.unitType == type.whatBuilds().first || it.buildType == type || it.unitType == type }) {
        if (type.whatBuilds().first == UnitType.Zerg_Larva) {
            val hatcheries = UnitReservation.availableItems
                    .filter { it.unitType == UnitType.Zerg_Larva && it.buildType == UnitType.None }
                    .groupBy { it.hatchery }
                    .entries
            val hatchery = hatcheries.firstOrNull { it.value.size >= 3 }
                    ?: hatcheries.minBy {
                        if (it.key == null) Int.MAX_VALUE
                        else if (type.isWorker) units.myWorkers.inRadius(it.key, 300).size
                        else -units.myWorkers.inRadius(it.key, 300).size
                    }
            hatchery?.value?.first()
        } else
            UnitReservation.availableItems.firstOrNull { it.unitType == type.whatBuilds().first }
    }
    val costLock = costLocks.unitCostLock(type)
}

class PrepareTrain(private val board: TrainBoard) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Selector(
                    LambdaNode(this::checkStarted),
                    Parallel(Parallel.Policy.SELECTOR,
                            Parallel(Parallel.Policy.SEQUENCE,
                                    GetStuffToTrainOrBuild(board.type),
                                    WaitForUnitLock(board.trainerLock),
                                    WaitForCostLock(board.costLock),
                                    Repeat(Repeat.Policy.SELECTOR, Condition { requiredUnitsAreCompleted() })
                            ),
                            LambdaNode(this::registerAsPlanned)
                    )
            )

    override fun reset() {
        super.reset()
        board.trainee = null
    }

    private fun requiredUnitsAreCompleted() =
            board.type.requiredUnits().keys.all { reqType -> units.mine.any { it.unitType == reqType && it.isCompleted } }


    private fun checkStarted(): NodeStatus {
        if (economy.currentResources.minerals > 500) {
//            System.err.println("SAY WAT")
        }
        val trainer = board.trainerLock.item ?: return NodeStatus.FAILURE
        return if (trainer.buildType == board.type || trainer.unitType == board.type) {
            board.trainee = trainer
            NodeStatus.SUCCESS
        } else
            NodeStatus.FAILURE
    }

    private fun registerAsPlanned(): NodeStatus {
        Styx.buildPlan.plannedUnits += PlannedUnit(board.type, consumedUnit = board.type.whatBuilds().first)
        return NodeStatus.RUNNING
    }
}

class OrderTrain(private val board: TrainBoard) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Selector(
                    Condition { board.trainee != null },
                    LambdaNode(this::orderTrain)
            )

    private fun orderTrain(): NodeStatus {
        val trainer = board.trainerLock.item ?: error("Trainer must be locked")
        BasicActions.train(trainer, board.type)
        return NodeStatus.RUNNING
    }
}

/**
 * Trigger training of unit, don't wait for completion
 */
class StartTrain(private val board: TrainBoard) : BehaviorTree() {
    constructor(type: UnitType) : this(TrainBoard(type))

    override fun getRoot(): TreeNode = Memo(
            Sequence(
                    PrepareTrain(board),
                    OrderTrain(board)
            )
    )
}


/**
 * Trigger training of unit, wait for completion
 */
class Train(private val board: TrainBoard) : BehaviorTree() {
    constructor(type: UnitType) : this(TrainBoard(type))

    override fun getRoot(): TreeNode = Memo(
            Sequence(
                    StartTrain(board),
                    Repeat(Repeat.Policy.SELECTOR, Condition {
                        board.trainee?.isCompleted == true
                    })
            )
    )
}