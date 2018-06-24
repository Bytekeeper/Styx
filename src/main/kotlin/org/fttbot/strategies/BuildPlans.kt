package org.fttbot.strategies

import com.badlogic.gdx.math.MathUtils
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MParallel.Companion.mparallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.EnemyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.strategies.Strategies.considerBaseDefense
import org.fttbot.task.Macro
import org.fttbot.task.Macro.buildExpansion
import org.fttbot.task.Production
import org.fttbot.task.Production.build
import org.fttbot.task.Production.buildGas
import org.fttbot.task.Production.produce
import org.fttbot.task.Production.produceSupply
import org.fttbot.task.Production.research
import org.fttbot.task.Production.train
import org.fttbot.task.Production.trainWorker
import org.fttbot.task.Production.upgrade
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import kotlin.math.min

object BuildPlans {
    fun _12HatchA(): Node =
            mparallel(1000,
                    Repeat(5, Production.trainWorker()),
                    produceSupply(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool),
                    trainWorker(),
                    trainWorker()
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

    fun _3Hatch(): Node = mparallel(Int.MAX_VALUE,
            Repeat(8, trainWorker()),
            buildExpansion(),
            build(UnitType.Zerg_Spawning_Pool),
            considerBaseDefense(),
            train(UnitType.Zerg_Zergling),
            train(UnitType.Zerg_Zergling),
            trainWorker(),
            trainWorker(),
            trainWorker()
            )

    fun _3HatchMuta(): Node =
            mparallel(Int.MAX_VALUE,
                    Repeat(5, trainWorker()),
                    build(UnitType.Zerg_Spawning_Pool),
                    trainWorker(),
                    gasTrick(),
                    produceSupply(),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    Macro.buildExpansion(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    trainWorker(),
                    train(UnitType.Zerg_Zergling),
                    produceSupply(),
                    train(UnitType.Zerg_Zergling),
                    trainWorker(),
                    considerBaseDefense(),
                    USequence(
                            Utility({ min(Utilities.expansionUtility * 1.8, 1.0) }, Macro.buildExpansion()),
                            Utility({ Utilities.moreTrainersUtility }, Delegate { Production.build(UnitType.Zerg_Hatchery) }),
                            Utility({ Utilities.moreGasUtility * 0.8 }, Production.buildGas()),
                            Utility({ Utilities.moreSupplyUtility }, produceSupply()),
                            Utility({ Utilities.moreWorkersUtility }, trainWorker()),
                            Utility({ min(1.0, UnitQuery.myUnits.count { it is Worker } / (12.0 + UnitQuery.myUnits.count { it is Zergling })) }, train(UnitType.Zerg_Zergling))
                    ),
                    upgrade(UpgradeType.Metabolic_Boost),
                    upgrade(UpgradeType.Zerg_Carapace),
                    upgrade(UpgradeType.Zerg_Melee_Attacks)
            )

    fun _3HatchLurker(): Node =
            mparallel(1000,
                    _3Hatch(),
                    mainLurker()
            )


    fun _12poolMuta(): Node = mparallel(Int.MAX_VALUE,
            Repeat(7, trainWorker()),
            build(UnitType.Zerg_Spawning_Pool),
            buildGas(),
            trainWorker(),
            trainWorker(),
            buildExpansion(),
            build(UnitType.Zerg_Lair),
            Repeat(2, train(UnitType.Zerg_Zergling)),
            considerBaseDefense(),
            trainWorker(),
            trainWorker(),
            build(UnitType.Zerg_Spire),
            trainWorker(),
            produceSupply(),
            trainWorker(),
            trainWorker(),
            produceSupply(),
            produceSupply(),
            train(UnitType.Zerg_Mutalisk),
            train(UnitType.Zerg_Mutalisk),
            train(UnitType.Zerg_Mutalisk),
            train(UnitType.Zerg_Mutalisk),
            trainWorker(),
            mainMutasZergUltra()
    )

    fun overpoolVsZerg(): Node = mparallel(Int.MAX_VALUE,
            Repeat(5, trainWorker()),
            build(UnitType.Zerg_Spawning_Pool),
            trainWorker(),
            buildGas(),
            trainWorker(),
            trainWorker(),
            Repeat(child = fallback(Condition("Enough zerglings?") { UnitQuery.myUnits.count { it is Zergling || it is Egg && it.buildType == UnitType.Zerg_Zergling } >= 6 }, train(UnitType.Zerg_Zergling))),
            trainWorker(),
            trainWorker(),
            considerBaseDefense(),
            mainMutasZergUltra()
    )

    fun mainMutasZergUltra(): Node = mparallel(Int.MAX_VALUE,
            upgrade(UpgradeType.Metabolic_Boost),
            USequence(
                    Utility({ Utilities.expansionUtility }, fallback(Macro.buildExpansion(), Sleep)),
                    Utility({ Utilities.moreTrainersUtility }, fallback(Production.build(UnitType.Zerg_Hatchery), Sleep)),
                    Utility({ Utilities.moreGasUtility }, fallback(Production.buildGas(), Sleep)),
                    Utility({ Utilities.moreSupplyUtility }, produceSupply()),
                    Utility({ Utilities.moreWorkersUtility }, trainWorker()),
                    Utility({ min(1.0, UnitQuery.myWorkers.size / (UnitQuery.myUnits.count { it is Mutalisk } * 1.5 + 15.0)) }, train(UnitType.Zerg_Mutalisk)),
                    Utility({ min(1.0, UnitQuery.myUnits.count { it is Mutalisk } / (UnitQuery.myUnits.count { it is Ultralisk } * 3.0 + 25.0)) }, train(UnitType.Zerg_Ultralisk)),
                    Utility({ min(1.0, 5 / (0.1 + UnitQuery.myUnits.count { it is Zergling })) }, train(UnitType.Zerg_Zergling)),
                    Utility({
                        (UnitQuery.enemyUnits + EnemyInfo.seenUnits).count { it.isFlying && it !is Overlord } /
                                (UnitQuery.myUnits.count { it is Scourge || it is Egg && it.buildType == UnitType.Zerg_Scourge } + 2.1)
                    }, train(UnitType.Zerg_Scourge))
            ),
            upgrade(UpgradeType.Zerg_Flyer_Attacks)
//            Repeat(UpgradeType.Zerg_Flyer_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Attacks) }),
//            Repeat(UpgradeType.Zerg_Melee_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Melee_Attacks) }),
//            Repeat(UpgradeType.Zerg_Flyer_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Carapace) }),
//            upgrade(UpgradeType.Adrenal_Glands),
//            Repeat(UpgradeType.Zerg_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Carapace) }),
//            Repeat(UpgradeType.Zerg_Flyer_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Carapace) }),
//            upgrade(UpgradeType.Anabolic_Synthesis),
//            upgrade(UpgradeType.Chitinous_Plating),
//            Repeat(UpgradeType.Zerg_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Carapace) })
    )

    fun raceChoice(): Node = fallback(
            sequence(
                    Condition("Terran?") { FTTBot.enemies[0].race == Race.Terran },
                    Delegate {
                        if (MathUtils.random() > 0.2f) {
                            if (MathUtils.random() > 0.7f)
                                _3HatchLurker()
                            else
                                _12poolLurker()
                        } else
                            _2HatchMuta()
                    }
            ),
            sequence(
                    Condition("Zerg?") { FTTBot.enemies[0].race == Race.Zerg },
                    overpoolVsZerg()
            ),
            Delegate {
                if (MathUtils.random() > 0.9f) {
                    if (MathUtils.random() > 0.7f)
                        _3HatchLurker()
                    else
                        _12poolLurker()
                } else
                    _12poolMuta()
            }
    )

    fun raceChoice(): Node = fallback(
            sequence(
                    Condition("Terran?")
                    { FTTBot.enemies[0].race == Race.Terran },
                    Delegate
                    {
                        if (MathUtils.random() > 0.2f) {
                            if (MathUtils.random() > 0.7f)
                                _3HatchLurker()
                            else
                                _12poolLurker()
                        } else
                            _3HatchMuta()
                    }
            ),
            sequence(
                    Condition("Zerg?")
                    { FTTBot.enemies[0].race == Race.Zerg },
                    overpoolVsZerg()
            ),
            Delegate
            {
                if (MathUtils.random() > 0.9f) {
                    if (MathUtils.random() > 0.7f)
                        _3HatchLurker()
                    else
                        _12poolLurker()
                } else
                    _12poolMuta()
            }
    )

    fun mainLurker(): Node = mparallel(Int.MAX_VALUE,
            Macro.preventSupplyBlock(),
            considerExpansion(),
            Macro.considerGas(),
            upgrade(UpgradeType.Zerg_Carapace),
            upgrade(UpgradeType.Muscular_Augments),
            upgrade(UpgradeType.Grooved_Spines),
            Repeat(child = fallback(
                    sequence(
                            Condition("Not enough Hydras?") {
                                UnitQuery.myUnits.count { it is Hydralisk } / (1.0 + UnitQuery.myUnits.count { it is Lurker }) < 0.4
                            },
                            train(UnitType.Zerg_Hydralisk)
                    ),
                    train(UnitType.Zerg_Lurker))
            ),
            considerMoreTrainers(),
            Repeat(child = train(UnitType.Zerg_Zergling)),
            Repeat(UpgradeType.Zerg_Missile_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Missile_Attacks) }),
            Repeat(UpgradeType.Zerg_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Carapace) })
    )

    private fun uMoreWorkers() = Repeat(child = Utility({ Utilities.moreWorkersUtility }, trainWorker()))

    private fun uMoreSupply() = Repeat(child = Utility({ Utilities.moreSupplyUtility }, produceSupply()))

    fun _2HatchMuta(): Node =
            mparallel(1000,
                    Delegate {
                        if (MathUtils.randomBoolean()) {
                            _12HatchA()
                        } else {
                            _12HatchB()
                        }
                    },
                    buildGas(),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    considerBaseDefense(),
                    trainWorker(),
                    trainWorker(),
                    Strategies.considerBuildingWorkers(),
                    build(UnitType.Zerg_Lair),
                    produce(UnitType.Zerg_Spire),
                    Macro.buildExpansion(),
                    fallback(buildGas(), Success),
                    mainMutasZergUltra()
            )
}