package org.fttbot.strategies

import org.fttbot.*
import org.fttbot.task.Macro
import org.fttbot.task.Production
import org.fttbot.task.Production.build
import org.fttbot.task.Production.buildGas
import org.fttbot.task.Production.train
import org.fttbot.task.Production.trainWorker
import org.fttbot.task.Production.upgrade
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType

object ZvP {
    fun _12Hatch(): Node =
            MAll("12Hatch",
                    Repeat(5, MDelegate { Production.trainWorker() }),
                    train(UnitType.Zerg_Overlord),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _10Hatch(): Node =
            MSequence("10Hatch",
                    Repeat(5, MDelegate { Production.trainWorker() }),
                    Strategies.gasTrick(),
                    build(UnitType.Zerg_Hatchery),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _massZergling(): Node = MAll("10->mass",
            _10Hatch(),
            All("doit",
                    MSequence("massZerg",
                            Repeat(6, MDelegate { train(UnitType.Zerg_Zergling) }),
                            MDelegate { build(UnitType.Zerg_Hatchery) },
                            Repeat(node = MDelegate { train(UnitType.Zerg_Zergling) })),
                    Strategies.buildWorkers()
            )
    )

    fun xx(): Node = MAll("12->xx",
            _12Hatch(),
            All("xx",
                    Repeat(4, MDelegate { train(UnitType.Zerg_Zergling) }),
                    buildGas(),
                    build(UnitType.Zerg_Spire),
                    Repeat(node = MAll("MutaZerg",
                            train(UnitType.Zerg_Mutalisk),
                            train(UnitType.Zerg_Mutalisk),
                            train(UnitType.Zerg_Zergling))
                    ),
                    Strategies.buildWorkers(),
                    Sleep
            )
    )

}