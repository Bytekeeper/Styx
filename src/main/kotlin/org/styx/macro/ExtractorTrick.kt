package org.styx.macro

import bwapi.UnitType
import org.bk.ass.bt.*

class ExtractorTrick(unitToTrain: UnitType = UnitType.Zerg_Drone) : BehaviorTree() {
    private val extractorBoard = BuildBoard(UnitType.Zerg_Extractor)
    private val trainWorkerBoard = TrainBoard(unitToTrain)

    override fun getRoot(): TreeNode = Memo(
            Sequence(
                    // Never fail this part to make sure cancelling is called
                    Memo(Succeeder(Sequence(
                            Parallel(
                                    PrepareBuild(extractorBoard),
                                    PrepareTrain(trainWorkerBoard)
                            ),
                            Sequence(
                                    OrderBuild(extractorBoard),
                                    OrderTrain(trainWorkerBoard)
                            )
                    ))),
                    CancelBuild(extractorBoard)
            )
    ).withName("Extractor Trick")
}