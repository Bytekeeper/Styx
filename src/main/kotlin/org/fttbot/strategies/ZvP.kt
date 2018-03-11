package org.fttbot.strategies

import org.fttbot.*
import org.fttbot.task.Macro
import org.fttbot.task.Production
import org.fttbot.task.Production.build
import org.fttbot.task.Production.buildGas
import org.fttbot.task.Production.train
import org.openbw.bwapi4j.type.UnitType

object ZvP {
    fun _12Hatch(): Node =
            MAll(
                    Repeat(5, MDelegate { Production.trainWorker() }),
                    train(UnitType.Zerg_Overlord),
                    Production.trainWorker(),
                    Production.trainWorker(),
                    Production.trainWorker(),
                    Macro.buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool),
                    Repeat(4, MDelegate { train(UnitType.Zerg_Zergling) }),
                    Production.trainWorker(),
                    Production.trainWorker(),
                    Production.trainWorker(),
                    buildGas(),
                    Production.trainWorker(),
                    Production.trainWorker(),
                    Production.trainWorker(),
                    build(UnitType.Zerg_Spire)
            )
}