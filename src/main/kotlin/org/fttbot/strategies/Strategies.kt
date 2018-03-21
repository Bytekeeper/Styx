package org.fttbot.strategies

import org.fttbot.*
import org.fttbot.info.UnitQuery
import org.fttbot.task.Macro
import org.fttbot.task.Production
import org.fttbot.task.Production.cancelGas
import org.openbw.bwapi4j.unit.Egg
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Unit
import org.openbw.bwapi4j.unit.Worker

object Strategies {
    fun buildWorkers() =
            Fallback(Production.trainWorker() onlyIf Require {
                UnitQuery.myUnits.count { it is Worker && !it.isCompleted || it is Egg && it.buildType.isWorker } <
                        Info.myBases.count { it is PlayerUnit && it.isCompleted }
            }, Success)

    fun gasTrick() = MSequence("gasTrick",
            Production.buildGas(),
            Production.trainWorker(),
            cancelGas()
    )

    fun considerExpansion() = Fallback(
            MDelegate { Macro.buildExpansion() } onlyIf Require { UnitQuery.myWorkers.size / 11 >= Info.myBases.size },
            Success
    )

    fun moveSurplusWorkers() = Success
}