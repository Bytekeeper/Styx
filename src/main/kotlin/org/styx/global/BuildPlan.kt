package org.styx.global

import bwapi.UnitType
import org.bk.ass.bt.TreeNode
import org.bk.ass.manage.GMS
import org.styx.Styx

data class PlannedUnit(val type: UnitType, val framesToStart: Int? = null, val consumedUnit: UnitType? = null, val gmsWhilePlanning: GMS = Styx.resources.availableGMS)
data class Unrealized(val type: UnitType, val amount: Int)

class BuildPlan : TreeNode() {
    val plannedUnits = mutableListOf<PlannedUnit>()
    val unrealized = mutableListOf<Unrealized>()


    override fun exec() {
        success()
        plannedUnits.clear()
        unrealized.clear()
    }
}