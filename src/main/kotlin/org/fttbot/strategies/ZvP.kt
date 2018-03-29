package org.fttbot.strategies

import com.badlogic.gdx.math.MathUtils
import org.fttbot.Delegate
import org.fttbot.MParallel
import org.fttbot.Node
import org.fttbot.Repeat
import org.fttbot.task.Macro
import org.fttbot.task.Production
import org.fttbot.task.Production.build
import org.fttbot.task.Production.buildGas
import org.fttbot.task.Production.buildOrTrainSupply
import org.fttbot.task.Production.produce
import org.fttbot.task.Production.train
import org.fttbot.task.Production.trainWorker
import org.openbw.bwapi4j.type.UnitType

object ZvP {
    fun _12HatchA(): Node =
            MParallel(1000,
                    Repeat(5, Delegate { Production.trainWorker() }),
                    buildOrTrainSupply(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _12HatchB(): Node =
            MParallel(1000,
                    Repeat(5, Delegate { Production.trainWorker() }),
                    buildOrTrainSupply(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _2HatchMuta(): Node =
            MParallel(1000,
                    Delegate {
                        if (MathUtils.randomBoolean()) {
                            _12HatchA()
                        } else {
                            _12HatchB()
                        }
                    },
                    buildGas(),
                    trainWorker(),
                    trainWorker(),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    trainWorker(),
                    trainWorker(),
                    build(UnitType.Zerg_Lair),
                    trainWorker(),
                    trainWorker(),
                    buildOrTrainSupply(),
                    trainWorker(),
                    trainWorker(),
                    produce(UnitType.Zerg_Spire),
                    Macro.buildExpansion(),
                    Repeat(child = Delegate { Strategies.buildWorkers() }),
                    buildGas(),
                    buildOrTrainSupply(),
                    buildOrTrainSupply(),
                    Repeat(child = Delegate { train(UnitType.Zerg_Mutalisk) }),
                    Repeat(child = Delegate { Strategies.considerExpansion() }),
                    Repeat(child = Delegate { Macro.considerGas() })
            )
}