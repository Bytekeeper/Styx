package org.styx.macro

import bwapi.UnitType
import org.styx.*
import org.styx.action.BasicActions

/**
 * Trigger training of unit, don't wait for completion
 */
class Train(private val type: UnitType) : MemoLeaf() {
    private val trainerLock = UnitLock({ it.unitType == type.whatBuilds().first || it.buildType == type }) { Styx.resources.availableUnits.firstOrNull { it.unitType == type.whatBuilds().first } }
    private val costLock = UnitCostLock(type)
    private val dependency = Par("Dependencies for ${type}",
            *type.requiredUnits()
                    .filter { (type, _) -> type != UnitType.Zerg_Larva }
                    .map { (type, amount) ->
                        Get(amount, type)
                    }.toTypedArray())
    var trainedUnit: SUnit? = null
        private set

    override fun tick(): NodeStatus {
        val dependencyStatus = dependency.perform()
        if (dependencyStatus == NodeStatus.FAILED)
            return NodeStatus.FAILED
        if (trainerLock.unit?.unitType == type)
            return NodeStatus.DONE
        trainerLock.acquire()
        if (trainerLock.unit?.buildType == type) {
            trainedUnit = trainerLock.unit
            return NodeStatus.RUNNING
        }
        trainedUnit = null
        Styx.buildPlan.plannedUnits += PlannedUnit(type)
        costLock.acquire()
        if (!costLock.satisfied)
            return NodeStatus.RUNNING
        if (type.requiredUnits().keys.any { reqType -> Styx.units.mine.none { it.unitType == reqType && it.isCompleted } })
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