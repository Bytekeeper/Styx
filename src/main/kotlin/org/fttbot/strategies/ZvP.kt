package org.fttbot.strategies

import com.badlogic.gdx.math.MathUtils
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MParallel.Companion.mparallel
import org.fttbot.Parallel.Companion.parallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.EnemyInfo
import org.fttbot.info.UnitQuery
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
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.Hydralisk
import org.openbw.bwapi4j.unit.Lurker
import org.openbw.bwapi4j.unit.Scourge

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

    fun overpool(): Node = mparallel(Int.MAX_VALUE,
            Repeat(5, trainWorker()),
            produceSupply(),
            build(UnitType.Zerg_Spawning_Pool),
            trainWorker(),
            buildGas(),
            trainWorker(),
            trainWorker(),
            Repeat(6, train(UnitType.Zerg_Zergling)),
            considerWorkers(),
            considerBaseDefense(),
            build(UnitType.Zerg_Spire),
            build(UnitType.Zerg_Creep_Colony),
            Repeat(child = train(UnitType.Zerg_Mutalisk)),
            Repeat(child = train(UnitType.Zerg_Zergling)),
            build(UnitType.Zerg_Hatchery),
            Repeat(child = fallback(sequence(
                    Condition("Enemy has air?") { EnemyInfo.seenUnits.any { it.isFlying } },
                    train(UnitType.Zerg_Scourge)), Sleep)),
            considerExpansion(),
            considerMoreTrainers(),
            Repeat(UpgradeType.Zerg_Flyer_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Attacks) }),
            upgrade(UpgradeType.Metabolic_Boost),
            upgrade(UpgradeType.Adrenal_Glands)
    )

    fun raceChoice(): Node = fallback(
            sequence(
                    Condition("Terran?") { FTTBot.enemy.race == Race.Terran },
                    _3HatchLurker()
            ),
            sequence(
                    Condition("Zerg?") { FTTBot.enemy.race == Race.Zerg },
                    overpool()
            ),
            Delegate {
                if (MathUtils.randomBoolean()) {
                    _2HatchMuta()
                } else {
                    overpool()
                }
            }
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
                            Repeat(child = fallback(Condition("Enough Lurkers?") {
                                UnitQuery.myUnits.count { it is Lurker } > UnitQuery.myUnits.count { it is Hydralisk }
                            }, train(UnitType.Zerg_Lurker))),
                            Repeat(child = train(UnitType.Zerg_Hydralisk)),
                            Repeat(child = train(UnitType.Zerg_Zergling))
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
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    train(UnitType.Zerg_Zergling),
                    buildGas(),
                    trainWorker(),
                    trainWorker(),
                    considerBaseDefense(),
                    Strategies.considerWorkers(),
                    build(UnitType.Zerg_Lair),
                    produce(UnitType.Zerg_Spire),
                    Macro.buildExpansion(),
                    fallback(buildGas(), Success),
                    considerScourge(),
                    Repeat(child = train(UnitType.Zerg_Mutalisk)),
                    considerExpansion(),
                    Repeat(50, child = Delegate { train(UnitType.Zerg_Zergling) }),
                    Macro.considerGas(),
                    Repeat(child = train(UnitType.Zerg_Ultralisk)),
                    Repeat(child = train(UnitType.Zerg_Zergling)),
                    considerMoreTrainers(),
                    upgrade(UpgradeType.Zerg_Flyer_Attacks),
                    upgrade(UpgradeType.Metabolic_Boost),
                    upgrade(UpgradeType.Adrenal_Glands),
                    Repeat(UpgradeType.Zerg_Melee_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Melee_Attacks) }),
                    Repeat(UpgradeType.Zerg_Flyer_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Attacks) }),
                    Repeat(UpgradeType.Zerg_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Carapace) }),
                    Repeat(UpgradeType.Zerg_Flyer_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Carapace) })
            )

    private fun considerScourge(): Node {
        return Repeat(child = fallback(sequence(
                Condition("Enemy has air?") { UnitQuery.myUnits.count { it is Scourge } < EnemyInfo.seenUnits.count { it.isFlying } },
                train(UnitType.Zerg_Scourge)
        ), Sleep))
    }

}