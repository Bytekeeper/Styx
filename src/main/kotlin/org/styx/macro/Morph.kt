package org.styx.macro

import bwapi.UnitType
import org.bk.ass.bt.*
import org.bk.ass.manage.GMS
import org.bk.ass.manage.Lock
import org.styx.SUnit
import org.styx.Styx
import org.styx.UnitLock
import org.styx.action.BasicActions
import org.styx.costLocks
import org.styx.global.PlannedUnit

data class MorphBoard(
        val type: UnitType,
        var unit: SUnit? = null
) {
    val morphLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == type.whatBuilds().first && it.isCompleted } }
    val costLock: Lock<GMS> = costLocks.unitCostLock(type)
}

class StartMorph(private val board: MorphBoard) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Selector(
                    Sequence(
                            AcquireCostLock(board.costLock),
                            AcquireUnitLock(board.morphLock),
                            LambdaNode {
                                board.unit = board.morphLock.item
                                BasicActions.morph(board.morphLock.item, board.type)
                                return@LambdaNode NodeStatus.RUNNING
                            }
                    ),
                    NodeStatus.RUNNING.after() {
                        board.unit = null
                        val type = board.type
                        Styx.buildPlan.plannedUnits += PlannedUnit(type, consumedUnit = if (type.isBuilding) null else type.whatBuilds().first)
                    }
            )

    override fun reset() {
        super.reset()
        board.unit = null
    }
}

class Morph(private val board: MorphBoard) : BehaviorTree() {
    constructor(type: UnitType) : this(MorphBoard(type))

    override fun getRoot(): TreeNode = Memo(
            Selector(
                    Condition { board.unit?.unitType == board.type },
                    StartMorph(board)
            )
    )
}
