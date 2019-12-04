package org.styx.macro

import bwapi.UnitType
import org.styx.*
import org.styx.action.BasicActions

class Morph(private val type: UnitType) : MemoLeaf() {
    private val morphLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == type.whatBuilds().first } }
    private val costLock = UnitCostLock(type)

    override fun tick(): NodeStatus {
        if (morphLock.unit?.unitType == type)
            return NodeStatus.DONE
        costLock.acquire()
        if (costLock.satisfied) {
            morphLock.acquire()
            val unitToMorph = morphLock.unit ?: run {
                Styx.buildPlan.plannedUnits += PlannedUnit(type)
                return NodeStatus.RUNNING
            }
            BasicActions.morph(unitToMorph, type)
        } else {
            Styx.buildPlan.plannedUnits += PlannedUnit(type)
        }
        return NodeStatus.RUNNING
    }
}