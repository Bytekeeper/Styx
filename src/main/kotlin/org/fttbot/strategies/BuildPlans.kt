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
                    _3Hatch(),
                    mainMutasZergUltra()
            )

    fun _12poolMuta(): Node = mparallel(Int.MAX_VALUE,
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
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

    fun _12poolLurker(): Node = mparallel(Int.MAX_VALUE,
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            build(UnitType.Zerg_Spawning_Pool),
            trainWorker(),
            trainWorker(),
            buildGas(),
            buildExpansion(),
            build(UnitType.Zerg_Lair),
            Repeat(2, train(UnitType.Zerg_Zergling)),
            build(UnitType.Zerg_Hydralisk_Den),
            trainWorker(),
            trainWorker(),
            research(TechType.Lurker_Aspect),
            produceSupply(),
            train(UnitType.Zerg_Lurker),
            train(UnitType.Zerg_Lurker),
            train(UnitType.Zerg_Lurker),
            train(UnitType.Zerg_Lurker),
            considerBaseDefense(),
            mainLurker()
    )

    fun overpoolVsZerg(): Node = mparallel(Int.MAX_VALUE,
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            produceSupply(),
            build(UnitType.Zerg_Spawning_Pool),
            considerBaseDefense(),
            mainMutasZergUltra()
    )

    fun mainMutasZergUltra(): Node = mparallel(Int.MAX_VALUE,
            UParallel(Int.MAX_VALUE,
                    uExpand(),
                    uMoreHatches(),
                    uMoreGas(),
                    uMoreSupply(),
                    uMoreWorkers(),
                    Repeat(child = Utility({ Utilities.moreMutasUtility }, train(UnitType.Zerg_Mutalisk))),
                    Repeat(child = Utility({ Utilities.moreUltrasUtility }, train(UnitType.Zerg_Ultralisk))),
                    Repeat(child = Utility({ Utilities.moreLingsUtility }, train(UnitType.Zerg_Zergling))),
                    Utility({
                        (UnitQuery.enemyUnits + EnemyInfo.seenUnits).count { it.isFlying && it !is Overlord } /
                                (UnitQuery.myUnits.count { it is Scourge || it is Egg && it.buildType == UnitType.Zerg_Scourge } + 2.1)
                    }, train(UnitType.Zerg_Scourge)),
                    NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is AirAttacker } / (15.0 + 15 * FTTBot.self.getUpgradeLevel(UpgradeType.Zerg_Flyer_Attacks))) }, upgrade(UpgradeType.Zerg_Flyer_Attacks))),
                    NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is AirAttacker } / (20.0 + 15 * FTTBot.self.getUpgradeLevel(UpgradeType.Zerg_Flyer_Carapace))) }, upgrade(UpgradeType.Zerg_Flyer_Carapace))),
                    uMeleeAttacksUpgrade(),
                    uGroundArmorUpgrade(),
                    uMetabolicBoost(),
                    NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Zergling } / 50.0) }, upgrade(UpgradeType.Adrenal_Glands))),
                    NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Ultralisk } / 10.0) }, upgrade(UpgradeType.Anabolic_Synthesis))),
                    NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Ultralisk } / 12.0) }, upgrade(UpgradeType.Chitinous_Plating)))
            )
    )

    private fun uMetabolicBoost() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Zergling } / 20.0) }, upgrade(UpgradeType.Metabolic_Boost)))

    private fun uGroundArmorUpgrade() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is GroundAttacker } / (65.0 + 20 * FTTBot.self.getUpgradeLevel(UpgradeType.Zerg_Carapace))) }, upgrade(UpgradeType.Zerg_Carapace)))

    private fun uMeleeAttacksUpgrade() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is GroundAttacker } / (60.0 + 20 * FTTBot.self.getUpgradeLevel(UpgradeType.Zerg_Melee_Attacks))) }, upgrade(UpgradeType.Zerg_Melee_Attacks)))

    private fun uMoreGas() = NoFail(Utility({ Utilities.moreGasUtility }, buildGas()))

    private fun uMoreHatches() =
            NoFail(Utility({ Utilities.moreTrainersUtility }, fallback(build(UnitType.Zerg_Hatchery), Sleep)))

    private fun uExpand() = NoFail(Utility({ Utilities.expansionUtility }, buildExpansion()))

    fun raceChoice(): Node = fallback(
            sequence(
                    Condition("Terran?")
                    { FTTBot.enemies[0].race == Race.Terran },
                    Delegate
                    {
                        if (MathUtils.random() > 0.2f) {
                            _12poolLurker()
                        } else
                            _3HatchMuta()
                    }
            ),
            sequence(
                    Condition("Zerg?")
                    { FTTBot.enemies[0].race == Race.Zerg },
                    Delegate {
                        if (MathUtils.random() < 0.7)
                            overpoolVsZerg()
                        else
                            _3HatchMuta()
                    }
            ),
            Delegate
            {
                if (MathUtils.random() > 0.9f) {
                    _12poolLurker()
                } else
                    _12poolMuta()
            }
    )

    fun mainLurker(): Node = mparallel(Int.MAX_VALUE,
            UParallel(Int.MAX_VALUE,
                    uExpand(),
                    uMoreHatches(),
                    uMoreGas(),
                    uMoreSupply(),
                    uMoreWorkers(),
                    Repeat(child = Utility({ Utilities.moreLurkersUtility }, train(UnitType.Zerg_Lurker))),
                    Repeat(child = Utility({ Utilities.moreHydrasUtility }, train(UnitType.Zerg_Hydralisk))),
                    NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Lurker || it is Hydralisk } / (10.0 + 20 * FTTBot.self.getUpgradeLevel(UpgradeType.Zerg_Missile_Attacks))) }, upgrade(UpgradeType.Zerg_Missile_Attacks))),
                    NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Hydralisk } / (12.0 + 20 * FTTBot.self.getUpgradeLevel(UpgradeType.Grooved_Spines))) }, upgrade(UpgradeType.Grooved_Spines))),
                    NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Hydralisk } / (10.0 + 20 * FTTBot.self.getUpgradeLevel(UpgradeType.Muscular_Augments))) }, upgrade(UpgradeType.Muscular_Augments))),
                    uGroundArmorUpgrade(),
                    uMetabolicBoost(),
                    Repeat(child = Utility({ min(1.0, 3 / (0.1 + UnitQuery.myUnits.count { it is Zergling })) }, train(UnitType.Zerg_Zergling)))
            ),
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