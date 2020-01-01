package org.styx.macro

import bwapi.UnitType
import org.bk.ass.bt.BehaviorTree
import org.bk.ass.bt.Memo
import org.bk.ass.bt.Sequence
import org.bk.ass.bt.TreeNode

class ExtractorTrick() : BehaviorTree() {
    private val extractorBoard = BuildBoard(UnitType.Zerg_Extractor)

    override fun getRoot(): TreeNode = Memo(
            Sequence(
                    StartBuild(extractorBoard),
                    StartTrain(UnitType.Zerg_Drone),
                    CancelBuild(extractorBoard)
            )
    )
}