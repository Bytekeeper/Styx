package org.fttbot.strategies

import com.badlogic.gdx.math.MathUtils
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MParallel.Companion.mparallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.EnemyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.strategies.BuildPlans._12HatchA
import org.fttbot.strategies.BuildPlans._12HatchB
import org.fttbot.strategies.BuildPlans.mainMutasZergUltra
import org.fttbot.strategies.Strategies.considerBaseDefense
import org.fttbot.strategies.Strategies.considerBuildingWorkers
import org.fttbot.strategies.Strategies.considerMoreTrainers
import org.fttbot.task.Macro
import org.fttbot.task.Macro.buildExpansion
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
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.Egg
import org.openbw.bwapi4j.unit.Overlord
import org.openbw.bwapi4j.unit.Scourge
import org.openbw.bwapi4j.unit.Zergling

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


    fun _12poolLurker(): Node = mparallel(Int.MAX_VALUE,
            Repeat(7, trainWorker()),
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
            considerBuildingWorkers(),
            considerBaseDefense(),
            mainLurker()
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
            considerBuildingWorkers(),
            Macro.preventSupplyBlock(),
            considerScourge(),
            train(UnitType.Zerg_Mutalisk),
            train(UnitType.Zerg_Mutalisk),
            train(UnitType.Zerg_Mutalisk),
            train(UnitType.Zerg_Mutalisk),
            train(UnitType.Zerg_Mutalisk),
            considerExpansion(),
            Macro.considerGas(),
            train(UnitType.Zerg_Mutalisk),
            train(UnitType.Zerg_Mutalisk),
            train(UnitType.Zerg_Mutalisk),
            Repeat(child = train(UnitType.Zerg_Mutalisk)),
            Repeat(10, child = Delegate { train(UnitType.Zerg_Zergling) }),
            considerMoreTrainers(),
            Repeat(child = train(UnitType.Zerg_Ultralisk)),
            Repeat(child = train(UnitType.Zerg_Zergling)),
            upgrade(UpgradeType.Zerg_Flyer_Attacks),
            upgrade(UpgradeType.Metabolic_Boost),
            Repeat(UpgradeType.Zerg_Flyer_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Attacks) }),
            Repeat(UpgradeType.Zerg_Melee_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Melee_Attacks) }),
            Repeat(UpgradeType.Zerg_Flyer_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Carapace) }),
            upgrade(UpgradeType.Adrenal_Glands),
            Repeat(UpgradeType.Zerg_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Carapace) }),
            Repeat(UpgradeType.Zerg_Flyer_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Carapace) }),
            upgrade(UpgradeType.Anabolic_Synthesis),
            upgrade(UpgradeType.Chitinous_Plating),
            Repeat(UpgradeType.Zerg_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Carapace) })
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

    fun _3HatchLurker(): Node =
            mparallel(1000,
                    Repeat(5, trainWorker()),
                    produceSupply(),
                    Repeat(3, trainWorker()),
                    buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool),
                    Repeat(3, trainWorker()),
                    build(UnitType.Zerg_Hatchery),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    considerBuildingWorkers(),
                    considerBaseDefense(),
                    buildGas(),
                    build(UnitType.Zerg_Lair),
                    upgrade(UpgradeType.Metabolic_Boost),
                    build(UnitType.Zerg_Hydralisk_Den),
                    buildGas(),
                    research(TechType.Lurker_Aspect),
                    build(UnitType.Zerg_Evolution_Chamber),
                    train(UnitType.Zerg_Lurker),
                    train(UnitType.Zerg_Lurker),
                    train(UnitType.Zerg_Lurker),
                    train(UnitType.Zerg_Lurker),
                    train(UnitType.Zerg_Lurker),
                    train(UnitType.Zerg_Lurker),
                    train(UnitType.Zerg_Lurker),
                    mainLurker()
            )

    fun mainLurker(): Node = mparallel(Int.MAX_VALUE,
            Macro.preventSupplyBlock(),
            considerExpansion(),
            Macro.considerGas(),
            upgrade(UpgradeType.Zerg_Carapace),
            Repeat(child = train(UnitType.Zerg_Lurker)),
            considerMoreTrainers(),
            Repeat(child = train(UnitType.Zerg_Zergling)),
            upgrade(UpgradeType.Muscular_Augments),
            upgrade(UpgradeType.Grooved_Spines),
            Repeat(UpgradeType.Zerg_Missile_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Missile_Attacks) }),
            Repeat(UpgradeType.Zerg_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Carapace) })
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
                    mainMutasZergUltra()
            )

    private fun considerScourge(): Node {
        return Repeat(child = fallback(sequence(
                Condition("Enemy has air?") { UnitQuery.myUnits.count { it is Scourge } < (UnitQuery.enemyUnits + EnemyInfo.seenUnits).count { it.isFlying && it !is Overlord } },
                Inline("Blub") {
                    NodeStatus.SUCCEEDED
                },
                train(UnitType.Zerg_Scourge)
        ), Sleep))
    }

}