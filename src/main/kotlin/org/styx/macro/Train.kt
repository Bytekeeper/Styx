package org.styx.macro

import bwapi.UnitType
import org.styx.*
import org.styx.Styx.units
import org.styx.action.BasicActions
import org.styx.global.PlannedUnit


class AcquireUnitLock(private val lock: UnitLock) : BTNode() {
    override fun tick(): NodeStatus {
        lock.acquire()
        return if (lock.satisfied)
            NodeStatus.DONE
        else
            NodeStatus.FAILED
    }
}

class WaitForCostLock(private val lock: GMSLock) : BTNode() {
    override fun tick(): NodeStatus {
        lock.acquire()
        return if (lock.satisfied)
            NodeStatus.DONE
        else
            NodeStatus.RUNNING
    }
}

data class TrainBoard(
        val type: UnitType,
        var trainee: SUnit? = null
) {
    init {
        require(!type.isBuilding)
    }

    val trainerLock = UnitLock({ it.unitType == type.whatBuilds().first || it.buildType == type || it.unitType == type }) {
        if (type.whatBuilds().first == UnitType.Zerg_Larva) {
            val hatcheries = Styx.resources.availableUnits
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
            Styx.resources.availableUnits.firstOrNull { it.unitType == type.whatBuilds().first }
    }
    val costLock = UnitCostLock(type)
}

class PrepareTrain(private val board: TrainBoard) : BehaviorTree("Prepare Training of ${board.type}") {
    override fun buildRoot(): SimpleNode =
            Sel("Check done or prepare",
                    this::checkStarted,
                    Sel("Prep done, else register planned",
                            DoneFilter(Par("Ensure deps, trainer and cost", true,
                                    EnsureDependenciesFor(board.type),
                                    AcquireUnitLock(board.trainerLock),
                                    WaitForCostLock(board.costLock),
                                    Condition { requiredUnitsAreCompleted() }
                            )),
                            this::registerAsPlanned
                    )
            )

    override fun reset() {
        super.reset()
        board.trainerLock.reset()
        board.trainee = null
    }

    private fun requiredUnitsAreCompleted() =
            board.type.requiredUnits().keys.all { reqType -> units.mine.any { it.unitType == reqType && it.isCompleted } }


    private fun checkStarted(): NodeStatus {
        val trainer = board.trainerLock.unit ?: return NodeStatus.FAILED
        return if (trainer.buildType == board.type || trainer.unitType == board.type) {
            board.trainee = trainer
            NodeStatus.DONE
        } else
            NodeStatus.FAILED
    }

    private fun registerAsPlanned(): NodeStatus {
        Styx.buildPlan.plannedUnits += PlannedUnit(board.type, consumedUnit = board.type.whatBuilds().first)
        return NodeStatus.RUNNING
    }
}

class OrderTrain(private val board: TrainBoard) : BehaviorTree("Order train ${board.type}") {
    override fun buildRoot(): SimpleNode = Sel("Start train",
            Condition { board.trainee != null },
            this::orderTrain
    )

    private fun orderTrain(): NodeStatus {
        val trainer = board.trainerLock.unit ?: error("Trainer must be locked")
        BasicActions.train(trainer, board.type)
        return NodeStatus.RUNNING
    }
}

/**
 * Trigger training of unit, don't wait for completion
 */
class StartTrain(private val board: TrainBoard) : BehaviorTree("Start Training ${board.type}") {
    constructor(type: UnitType) : this(TrainBoard(type))

    override fun buildRoot(): SimpleNode = Memo(
            Seq(name,
                    PrepareTrain(board),
                    OrderTrain(board)
            )
    )
}


/**
 * Trigger training of unit, wait for completion
 */
class Train(private val board: TrainBoard) : BehaviorTree("Train ${board.type}") {
    constructor(type: UnitType) : this(TrainBoard(type))

    override fun buildRoot(): SimpleNode = Memo(
            Seq(name,
                    StartTrain(board),
                    WaitFor {
                        board.trainee?.isCompleted == true
                    }
            )
    )
}