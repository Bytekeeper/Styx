package org.fttbot.strategies

import org.fttbot.*
import org.fttbot.task.Macro
import org.fttbot.task.Production
import org.fttbot.task.Production.build
import org.fttbot.task.Production.buildGas
import org.fttbot.task.Production.train
import org.fttbot.task.Production.trainWorker
import org.openbw.bwapi4j.type.UnitType

object ZvP {
    fun _12Hatch(): Node =
            MParallel(1000,
                    Repeat(5, Delegate { Production.trainWorker() }),
                    train(UnitType.Zerg_Overlord),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _10Hatch(): Node =
            MSequence("10Hatch",
                    Repeat(5, Delegate { Production.trainWorker() }),
                    Strategies.gasTrick(),
                    build(UnitType.Zerg_Hatchery),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _massZergling(): Node = MParallel(1000,
            _10Hatch(),
            Strategies.considerExpansion(),
            MSequence("massZerg",
                    Repeat(6, Delegate { train(UnitType.Zerg_Zergling) }),
                    Delegate { build(UnitType.Zerg_Hatchery) },
                    Repeat(child = MSequence("tillDawn",
                            Strategies.buildWorkers(),
                            Repeat(2, Delegate { train(UnitType.Zerg_Zergling) })
                    )
                    )
            )
    )

    fun lurkers(): Node = MParallel(1000,
            _10Hatch(),
            Strategies.considerExpansion(),
            Repeat(6, Delegate { train(UnitType.Zerg_Zergling) }),
            Strategies.buildWorkers(),
            train(UnitType.Zerg_Hydralisk),
            train(UnitType.Zerg_Hydralisk),
            train(UnitType.Zerg_Hydralisk),
            train(UnitType.Zerg_Hydralisk),
            train(UnitType.Zerg_Lurker),
            train(UnitType.Zerg_Lurker),
            train(UnitType.Zerg_Lurker),
            train(UnitType.Zerg_Lurker),
            Repeat(child = MParallel(2,
                    train(UnitType.Zerg_Hydralisk),
                    train(UnitType.Zerg_Lurker)
            ))
    )

    fun xx(): Node = MParallel(1000,
            _12Hatch(),
            MParallel(1000,
                    Repeat(4, Delegate { train(UnitType.Zerg_Zergling) }),
                    buildGas(),
                    build(UnitType.Zerg_Spire),
                    Repeat(child = MParallel(1000,
                            train(UnitType.Zerg_Mutalisk),
                            train(UnitType.Zerg_Mutalisk),
                            train(UnitType.Zerg_Zergling))
                    ),
                    Strategies.buildWorkers(),
                    Sleep
            )
    )

}