package org.styx.macro

import bwapi.UnitType
import org.styx.*
import org.styx.action.BasicActions
import org.styx.global.PlannedUnit

data class MorphBoard(
        val type: UnitType,
        var unit: SUnit?
)

class Morph2(private val board: MorphBoard) : BehaviorTree("Morphing ${board.type}") {
    override fun buildRoot(): SimpleNode = Memo(
            Sel("Execute",
                    Condition { board.unit?.isCompleted == true }
                    )
    )

}

class Morph(private val type: UnitType) : MemoLeaf() {
    private val morphLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == type.whatBuilds().first && it.isCompleted } }
    private val costLock = UnitCostLock(type)

    override fun tick(): NodeStatus {
        if (morphLock.unit?.unitType == type)
            return NodeStatus.DONE
        costLock.acquire()
        if (costLock.satisfied) {
            morphLock.acquire()
            val unitToMorph = morphLock.unit ?: run {
                Styx.buildPlan.plannedUnits += PlannedUnit(type, consumedUnit = if (type.isBuilding) null else type.whatBuilds().first)
                return NodeStatus.RUNNING
            }
            BasicActions.morph(unitToMorph, type)
        } else {
            Styx.buildPlan.plannedUnits += PlannedUnit(type, consumedUnit = if (type.isBuilding) null else type.whatBuilds().first)
        }
        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        morphLock.reset()
    }

    override fun toString(): String = "Morph $type"
}