package org.fttbot.strategies

import com.badlogic.gdx.math.MathUtils
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MParallel.Companion.mparallel
import org.fttbot.Parallel.Companion.parallel
import org.fttbot.strategies.Strategies.considerBaseDefense
import org.fttbot.strategies.Strategies.considerMoreTrainers
import org.fttbot.strategies.Strategies.considerWorkers
import org.fttbot.strategies.Strategies.gasTrick
import org.fttbot.task.Macro
import org.fttbot.task.Macro.considerExpansion
import org.fttbot.task.Production
import org.fttbot.task.Production.build
import org.fttbot.task.Production.buildGas
import org.fttbot.task.Production.produce
import org.fttbot.task.Production.produceSupply
import org.fttbot.task.Production.research
import org.fttbot.task.Production.train
import org.fttbot.task.Production.trainWorker
import org.fttbot.task.Production.upgrade
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType

object ZvP {
    fun _12HatchA(): Node =
            mparallel(1000,
                    Repeat(5, Production.trainWorker()),
                    produceSupply(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _12HatchB(): Node =
            mparallel(1000,
                    Repeat(5, Production.trainWorker()),
                    produceSupply(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    trainWorker(),
                    trainWorker(),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _9Pool(): Node =
            mparallel(1000,
                    Repeat(5, trainWorker()),
                    build(UnitType.Zerg_Spawning_Pool),
                    trainWorker(),
                    gasTrick(),
                    produceSupply(),
                    Repeat(3, train(UnitType.Zerg_Zergling))
            )


    fun _3HatchLurker(): Node =
            mparallel(1000,
                    Delegate {
                        if (MathUtils.randomBoolean()) {
                            _12HatchA()
                        } else {
                            _12HatchB()
                        }
                    },
                    build(UnitType.Zerg_Hatchery),
                    Repeat(6, train(UnitType.Zerg_Zergling)),
                    produceSupply(),
                    considerWorkers(),
                    buildGas(),
                    build(UnitType.Zerg_Lair),
                    produceSupply(),
                    upgrade(UpgradeType.Metabolic_Boost),
                    build(UnitType.Zerg_Hydralisk_Den),
                    buildGas(),
                    research(TechType.Lurker_Aspect),
                    build(UnitType.Zerg_Evolution_Chamber),
                    Repeat(9, train(UnitType.Zerg_Hydralisk)),
                    Repeat(8, train(UnitType.Zerg_Lurker)),
                    upgrade(UpgradeType.Zerg_Carapace),
                    parallel(1000,
                            Repeat(1, train(UnitType.Zerg_Lurker)),
                            Repeat(child = train(UnitType.Zerg_Hydralisk))
                    ),
                    upgrade(UpgradeType.Muscular_Augments),
                    upgrade(UpgradeType.Grooved_Spines),
                    Repeat(UpgradeType.Zerg_Missile_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Missile_Attacks) }),
                    Repeat(UpgradeType.Zerg_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Carapace) }),
                    Macro.considerGas(),
                    considerExpansion(),
                    considerMoreTrainers()
            )

    fun _2HatchMuta(): Node =
            mparallel(1000,
                    Delegate {
                        if (MathUtils.randomBoolean()) {
                            _12HatchA()
                        } else {
                            _12HatchB()
                        }
                    },
                    Strategies.considerCanceling(),
                    buildGas(),
                    trainWorker(),
                    trainWorker(),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    considerBaseDefense(),
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
                    Strategies.considerWorkers(),
                    fallback(buildGas(), Success),
                    produceSupply(),
                    produceSupply(),
                    fallback(parallel(
                            1000,
                            Repeat(child = parallel(1000,
                                    train(UnitType.Zerg_Mutalisk),
                                    train(UnitType.Zerg_Mutalisk))),
                            Repeat(50, child = Delegate { train(UnitType.Zerg_Zergling) }),
                            Macro.considerGas(),
                            Repeat(child = train(UnitType.Zerg_Ultralisk)),
                            Repeat(child = train(UnitType.Zerg_Zergling))
                    ), Sleep),
                    considerExpansion(),
                    considerMoreTrainers(),
                    upgrade(UpgradeType.Zerg_Flyer_Attacks),
                    upgrade(UpgradeType.Metabolic_Boost),
                    upgrade(UpgradeType.Adrenal_Glands),
                    Repeat(UpgradeType.Zerg_Melee_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Melee_Attacks) }),
                    Repeat(UpgradeType.Zerg_Flyer_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Attacks) }),
                    Repeat(UpgradeType.Zerg_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Carapace) }),
                    Repeat(UpgradeType.Zerg_Flyer_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Carapace) })
            )

}