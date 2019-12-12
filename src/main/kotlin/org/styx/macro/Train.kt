package org.styx.macro

import bwapi.UnitType
import org.styx.*
import org.styx.Styx.units
import org.styx.action.BasicActions


class AcquireUnitLock(private val lock: UnitLock) : SimpleNode {
    override fun invoke(): NodeStatus {
        lock.acquire()
        return if (lock.satisfied)
            NodeStatus.DONE
        else
            NodeStatus.FAILED
    }
}

class WaitForCostLock(private val lock: GMSLock) : SimpleNode {
    override fun invoke(): NodeStatus {
        lock.acquire()
        return if (lock.satisfied)
            NodeStatus.DONE
        else
            NodeStatus.RUNNING
    }
}

/**
 * Trigger training of unit, don't wait for completion
 */
class StartTrain(private val type: UnitType) : BehaviorTree("Start Training $type") {
    init {
        require(!type.isBuilding)
    }

    private val trainerLock = UnitLock({ it.unitType == type.whatBuilds().first || it.buildType == type || it.unitType == type }) {
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

    private val costLock = UnitCostLock(type)
    var unitBeingTrained: SUnit? = null
        private set

    override val root: SimpleNode = Memo(
            Seq(name,
                    Par("Requirements", false,
                            EnsureDependenciesFor(type),
                            Sel("Done or reserve",
                                    Seq("Acquire Trainer, check if done",
                                            AcquireUnitLock(trainerLock),
                                            this::checkDone
                                    ),
                                    Seq("Train",
                                            this::registerAsPlanned,
                                            WaitForCostLock(costLock),
                                            WaitFor { requiredUnitsAreCompleted() },
                                            this::performTrain
                                    )
                            )
                    )
            )
    )

    private fun performTrain(): NodeStatus {
        val trainer = trainerLock.unit ?: return NodeStatus.RUNNING
        BasicActions.train(trainer, type)
        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        trainerLock.reset()
        unitBeingTrained = null
    }

    private fun checkDone(): NodeStatus {
        val trainer = trainerLock.unit!!
        return if (trainer.buildType == type || trainer.unitType == type) {
            unitBeingTrained = trainer
            NodeStatus.DONE
        } else
            NodeStatus.FAILED
    }

    private fun registerAsPlanned(): NodeStatus {
        Styx.buildPlan.plannedUnits += PlannedUnit(type)
        return NodeStatus.DONE
    }

    private fun requiredUnitsAreCompleted() =
            type.requiredUnits().keys.all { reqType -> units.mine.any { it.unitType == reqType && it.isCompleted } }
}


/**
 * Trigger training of unit, wait for completion
 */
class Train(type: UnitType) : BehaviorTree("Train $type") {
    init {
        require(!type.isBuilding)
    }

    private val startTrain = StartTrain(type)
    var trainedUnit: SUnit? = null
        private set

    override val root: SimpleNode = Memo(
            Seq(name,
                    startTrain,
                    NodeStatus.DONE.after {
                        trainedUnit = startTrain.unitBeingTrained
                    },
                    WaitFor {
                        trainedUnit!!.isCompleted
                    }
            )
    )
}