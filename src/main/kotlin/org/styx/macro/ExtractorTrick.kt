package org.styx.macro

import bwapi.UnitType
import org.styx.BehaviorTree
import org.styx.Memo
import org.styx.Seq
import org.styx.SimpleNode

class ExtractorTrick() : BehaviorTree("Extractor Trick") {
    private val extractorBoard = BuildBoard(UnitType.Zerg_Extractor)

    override fun buildRoot(): SimpleNode = Memo(
            Seq("Start Extractor and Drone",
                    StartBuild(extractorBoard),
                    StartTrain(UnitType.Zerg_Drone),
                    CancelBuild(extractorBoard)
            )
    )
}