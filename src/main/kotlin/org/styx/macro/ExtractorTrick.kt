package org.styx.macro

import bwapi.UnitType
import org.bk.ass.bt.BehaviorTree
import org.bk.ass.bt.Memo
import org.bk.ass.bt.Sequence
import org.bk.ass.bt.TreeNode

class ExtractorTrick(unitToTrain: UnitType = UnitType.Zerg_Drone) : BehaviorTree() {
    private val extractorBoard = BuildBoard(UnitType.Zerg_Extractor)
    private val trainBoard = TrainBoard(unitToTrain)

    override fun getRoot(): TreeNode = Memo(
            Sequence(
                    Memo(Simul(extractorBoard, trainBoard)),
                    CancelBuild(extractorBoard)
            )
    ).withName("Extractor Trick")
}