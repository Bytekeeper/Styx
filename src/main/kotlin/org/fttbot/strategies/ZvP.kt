package org.fttbot.strategies

import com.badlogic.gdx.math.MathUtils
import org.fttbot.*
import org.fttbot.info.MyInfo
import org.fttbot.task.Combat
import org.fttbot.task.Macro
import org.fttbot.task.Macro.considerExpansion
import org.fttbot.task.Production
import org.fttbot.task.Production.build
import org.fttbot.task.Production.buildGas
import org.fttbot.task.Production.produce
import org.fttbot.task.Production.produceSupply
import org.fttbot.task.Production.train
import org.fttbot.task.Production.trainWorker
import org.fttbot.task.Production.upgrade
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.PlayerUnit

object ZvP {
    fun _12HatchA(): Node<Any, Any> =
            MParallel(1000,
                    Repeat(5, Delegate { Production.trainWorker() }),
                    produceSupply(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _12HatchB(): Node<Any, Any> =
            MParallel(1000,
                    Repeat(5, Delegate { Production.trainWorker() }),
                    produceSupply(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _2HatchMuta(): Node<Any, Any> =
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
                    Repeat(child = Fallback(
                            Sequence(
                                    DispatchParallel({ MyInfo.myBases })
                                    {
                                        val base = it as PlayerUnit
                                        Sequence(
                                                Combat.shouldIncreaseDefense(base.position),
                                                train(UnitType.Zerg_Zergling, { base.tilePosition })
                                        )
                                    }
                            ),
                            Sleep
                    )),
                    trainWorker(),
                    trainWorker(),
                    build(UnitType.Zerg_Lair),
                    trainWorker(),
                    trainWorker(),
                    produceSupply(),
                    trainWorker(),
                    trainWorker(),
                    produce(UnitType.Zerg_Spire),
                    Macro.buildExpansion(),
                    Repeat(child = Delegate { Strategies.considerWorkers() }),
                    Fallback(buildGas(), Success),
                    produceSupply(),
                    produceSupply(),
                    Fallback(Parallel(
                            1,
                            Repeat(child = Delegate { train(UnitType.Zerg_Mutalisk) }),
                            Repeat(50, child = Delegate { train(UnitType.Zerg_Zergling) }),
                            Repeat(child = Delegate { considerExpansion() }),
                            Repeat(child = Delegate { Macro.considerGas() }),
                            Repeat(child = Delegate { train(UnitType.Zerg_Ultralisk) })
                    )),
                    upgrade(UpgradeType.Zerg_Flyer_Attacks),
                    upgrade(UpgradeType.Metabolic_Boost),
                    upgrade(UpgradeType.Adrenal_Glands),
                    upgrade(UpgradeType.Zerg_Melee_Attacks),
                    upgrade(UpgradeType.Zerg_Melee_Attacks),
                    upgrade(UpgradeType.Zerg_Melee_Attacks)
            )
}