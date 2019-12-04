package org.styx.macro

import bwapi.UnitType
import org.styx.*
import org.styx.Styx.units
import org.styx.action.BasicActions

/**
 * Trigger training of unit, don't wait for completion
 */
class Train(private val type: UnitType) : MemoLeaf() {
    init {
        require(!type.isBuilding)
    }
    private val trainerLock = UnitLock({ it.unitType == type.whatBuilds().first || it.buildType == type || it.unitType == type}) {
        if (type.whatBuilds().first == UnitType.Zerg_Larva) {
            val hatcheries = Styx.resources.availableUnits
                    .filter { it.unitType == UnitType.Zerg_Larva }
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
    private val dependency = EnsureDependenciesFor(type)
    var trainedUnit: SUnit? = null
        private set

    override fun tick(): NodeStatus {
        val dependencyStatus = dependency.perform()
        if (dependencyStatus == NodeStatus.FAILED)
            return NodeStatus.FAILED
        trainerLock.acquire()
        trainerLock.unit?.let {trainer ->
            if (trainer.unitType == type && trainer.isCompleted)
                return NodeStatus.DONE
            if (trainer.buildType == type || trainer.unitType == type) {
                trainedUnit = trainerLock.unit
                return NodeStatus.RUNNING
            }
        }
        trainedUnit = null
        Styx.buildPlan.plannedUnits += PlannedUnit(type)
        costLock.acquire()
        if (!costLock.satisfied)
            return NodeStatus.RUNNING
        if (type.requiredUnits().keys.any { reqType -> units.mine.none { it.unitType == reqType && it.isCompleted } })
            return NodeStatus.RUNNING
        val trainer = trainerLock.unit ?: return NodeStatus.RUNNING
        BasicActions.train(trainer, type)
        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        dependency.reset()
        trainerLock.reset()
    }

    override fun toString(): String = "Train $type"
}