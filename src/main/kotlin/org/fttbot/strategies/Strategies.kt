package org.fttbot.strategies

import org.fttbot.Fallback
import org.fttbot.MSequence
import org.fttbot.Require
import org.fttbot.Success
import org.fttbot.info.UnitQuery
import org.fttbot.task.Production
import org.fttbot.task.Production.cancel
import org.fttbot.task.Production.cancelGas
import org.openbw.bwapi4j.unit.Egg
import org.openbw.bwapi4j.unit.Worker

object Strategies {
    fun buildWorkers() =
            Fallback(Production.trainWorker() onlyIf Require {
                UnitQuery.myUnits.none { it is Worker && !it.isCompleted || it is Egg && it.buildType.isWorker }
            }, Success)

    fun gasTrick() = MSequence("gasTrick",
            Production.buildGas(),
            Production.trainWorker(),
            cancelGas()
    )
}