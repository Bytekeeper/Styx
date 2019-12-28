package org.styx.macro

import bwapi.UnitType
import org.styx.BehaviorTree
import org.styx.Par
import org.styx.Seq
import org.styx.SimpleNode

class Simul(vararg types: UnitType) : BehaviorTree("At once build/train ") {
    private val boards = types.map {
        when {
            it.isBuilding -> BuildBoard(it)
            else -> TrainBoard(it)
        }
    }

    override fun buildRoot(): SimpleNode =
            Seq("At once",
                    Par("Preparations", false,
                            *boards.map {
                                when (it) {
                                    is BuildBoard -> PrepareBuild(it)
                                    is TrainBoard -> PrepareTrain(it)
                                    else -> error("Invalid board $it")
                                }
                            }.toTypedArray()),
                    Par("Build/Train", false,
                            *boards.map {
                                when (it) {
                                    is BuildBoard -> OrderBuild(it)
                                    is TrainBoard -> OrderTrain(it)
                                    else -> error("Invalid board $it")
                                } as SimpleNode
                            }.toTypedArray()
                    )
            )
}