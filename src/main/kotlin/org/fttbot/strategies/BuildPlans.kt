package org.fttbot.strategies

import com.badlogic.gdx.math.MathUtils
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MParallel.Companion.mparallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.EnemyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.strategies.Strategies.considerBaseDefense
import org.fttbot.strategies.Upgrades.uAdrenalGlandsUpgrade
import org.fttbot.strategies.Upgrades.uAnabolicSynthesisUpgrade
import org.fttbot.strategies.Upgrades.uChitinousPlatingUpgrade
import org.fttbot.strategies.Upgrades.uFlyerArmorUpgrade
import org.fttbot.strategies.Upgrades.uGroundArmorUpgrade
import org.fttbot.strategies.Upgrades.uMeleeAttacksUpgrade
import org.fttbot.strategies.Upgrades.uMetabolicBoost
import org.fttbot.strategies.Upgrades.uUpgradeFlyerAttacks
import org.fttbot.strategies.Macro.buildExpansion
import org.fttbot.strategies.Macro.uExpand
import org.fttbot.strategies.Macro.uMoreGas
import org.fttbot.strategies.Macro.uMoreHatches
import org.fttbot.strategies.Macro.uMoreSupply
import org.fttbot.strategies.Macro.uMoreWorkers
import org.fttbot.strategies.Upgrades.uGrooveSpines
import org.fttbot.strategies.Upgrades.uMissileAttacks
import org.fttbot.strategies.Upgrades.uMuscularAugments
import org.fttbot.task.Production
import org.fttbot.task.Production.build
import org.fttbot.task.Production.buildGas
import org.fttbot.task.Production.produce
import org.fttbot.task.Production.produceSupply
import org.fttbot.task.Production.research
import org.fttbot.task.Production.train
import org.fttbot.task.Production.trainWorker
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*

object BuildPlans {
    fun _12HatchA(): Node =
            mparallel(1000,
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
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
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
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
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
            trainWorker(),
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
                    build(UnitType.Zerg_Spire),
                    mainStrategy()
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
            mainStrategy()
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
            mainStrategy()
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
            build(UnitType.Zerg_Spire),
            mainStrategy()
    )

    fun mainStrategy(): Node = mparallel(Int.MAX_VALUE,
            UParallel(Int.MAX_VALUE,
                    defaultStrategy(),
                    Repeat(child = Utility({ Utilities.moreMutasUtility }, train(UnitType.Zerg_Mutalisk))),
                    Repeat(child = Utility({ Utilities.moreUltrasUtility }, train(UnitType.Zerg_Ultralisk))),
                    Repeat(child = Utility({ Utilities.moreLingsUtility }, train(UnitType.Zerg_Zergling))),
                    Repeat(child = Utility({ Utilities.moreLurkersUtility }, train(UnitType.Zerg_Lurker))),
                    Repeat(child = Utility({ Utilities.moreHydrasUtility }, train(UnitType.Zerg_Hydralisk))),
                    Utility({
                        (UnitQuery.enemyUnits + EnemyInfo.seenUnits).count { it.isFlying && it !is Overlord } /
                                (UnitQuery.myUnits.count { it is Scourge || it is Egg && it.buildType == UnitType.Zerg_Scourge } + 2.1)
                    }, train(UnitType.Zerg_Scourge))
            )
    )

    fun defaultStrategy() = UParallel(Int.MAX_VALUE,
            uExpand(),
            uMoreHatches(),
            uMoreGas(),
            uMoreSupply(),
            uMoreWorkers(),
            considerUpgrades()
            )

    fun considerUpgrades(): Node = UParallel(Int.MAX_VALUE,
            uMetabolicBoost(),
            uMeleeAttacksUpgrade(),
            uGroundArmorUpgrade(),
            uMetabolicBoost(),
            uAdrenalGlandsUpgrade(),
            uAnabolicSynthesisUpgrade(),
            uChitinousPlatingUpgrade(),
            uUpgradeFlyerAttacks(),
            uFlyerArmorUpgrade(),
            uMissileAttacks(),
            uGrooveSpines(),
            uMuscularAugments()
    )

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
                    mainStrategy()
            )
}