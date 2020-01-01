package org.styx.macro

import bwapi.UnitType
import org.bk.ass.bt.BehaviorTree
import org.bk.ass.bt.Parallel
import org.bk.ass.bt.Sequence
import org.bk.ass.bt.TreeNode

class Simul(vararg types: UnitType) : BehaviorTree() {
    private val boards = types.map {
        when {
            it.isBuilding -> BuildBoard(it)
            else -> TrainBoard(it)
        }
    }

    override fun getRoot(): TreeNode =
            Sequence(
                    Parallel(Parallel.Policy.SEQUENCE,
                            *boards.map {
                                when (it) {
                                    is BuildBoard -> PrepareBuild(it)
                                    is TrainBoard -> PrepareTrain(it)
                                    else -> error("Invalid board $it")
                                }
                            }.toTypedArray()),
                    Parallel(Parallel.Policy.SEQUENCE,
                            *boards.map {
                                when (it) {
                                    is BuildBoard -> OrderBuild(it)
                                    is TrainBoard -> OrderTrain(it)
                                    else -> error("Invalid board $it")
                                } as TreeNode
                            }.toTypedArray()
                    )
            )
}