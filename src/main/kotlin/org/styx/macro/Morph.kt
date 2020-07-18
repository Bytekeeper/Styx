package org.styx.macro

import bwapi.UnitType
import org.bk.ass.bt.*
import org.bk.ass.manage.GMS
import org.bk.ass.manage.Lock
import org.styx.*
import org.styx.action.BasicActions
import org.styx.global.PlannedUnit

data class MorphBoard(
        val type: UnitType,
        var unit: SUnit? = null
) {
    val morphLock = UnitLock({ it.unitType == type.whatBuilds().first }) { UnitReservation.availableItems.firstOrNull { it.unitType == type.whatBuilds().first && it.isCompleted && it.framesToLive > type.buildTime() } }
    val costLock: Lock<GMS> = costLocks.unitCostLock(type)
}

class StartMorph(private val board: MorphBoard) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Parallel(Parallel.Policy.SELECTOR,
                    Parallel(
                            GetStuffToTrainOrBuild(board.type),
                            Sequence(
                                    AcquireLock(board.costLock),
                                    AcquireLock(board.morphLock),
                                    NodeStatus.RUNNING.after {
                                        board.unit = board.morphLock.item
                                        BasicActions.morph(board.morphLock.item, board.type)
                                    }
                            )
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
