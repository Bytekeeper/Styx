package org.fttbot.strategies

import com.badlogic.gdx.math.MathUtils
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MParallel.Companion.mparallel
import org.fttbot.Parallel.Companion.parallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.Cluster
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
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.Base
import org.openbw.bwapi4j.unit.PlayerUnit

object ZvP {
    fun _12HatchA(): Node<Any, Any> =
            mparallel(1000,
                    Repeat(5, Delegate { Production.trainWorker() }),
                    produceSupply(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _12HatchB(): Node<Any, Any> =
            mparallel(1000,
                    Repeat(5, Delegate { Production.trainWorker() }),
                    produceSupply(),
                    trainWorker(),
                    trainWorker(),
                    trainWorker(),
                    Macro.buildExpansion(),
                    trainWorker(),
                    trainWorker(),
                    build(UnitType.Zerg_Spawning_Pool)
            )

    fun _2HatchMuta(): Node<Any, Any> =
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
                    Repeat(child = Delegate { Strategies.considerWorkers() }),
                    fallback(buildGas(), Success),
                    produceSupply(),
                    produceSupply(),
                    fallback(parallel(
                            1000,
                            Repeat(child = parallel(1000,
                                    train(UnitType.Zerg_Mutalisk),
                                    train(UnitType.Zerg_Mutalisk))),
                            Repeat(child = Delegate { considerExpansion() }),
                            Repeat(50, child = Delegate { train(UnitType.Zerg_Zergling) }),
                            Repeat(child = Delegate { Macro.considerGas() }),
                            Repeat(child = Delegate { train(UnitType.Zerg_Ultralisk) }),
                            Repeat(child = Delegate { train(UnitType.Zerg_Zergling) })
                    ), Sleep),
                    Repeat(child = fallback(
                            sequence(
                                    Condition("Too much money?") { Board.resources.minerals > MyInfo.myBases.size * 300 },
                                    build(UnitType.Zerg_Hatchery)
                            ),
                            Sleep
                    )),
                    upgrade(UpgradeType.Zerg_Flyer_Attacks),
                    upgrade(UpgradeType.Metabolic_Boost),
                    upgrade(UpgradeType.Adrenal_Glands),
                    Repeat(UpgradeType.Zerg_Melee_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Melee_Attacks) }),
                    Repeat(UpgradeType.Zerg_Flyer_Attacks.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Attacks) }),
                    Repeat(UpgradeType.Zerg_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Carapace) }),
                    Repeat(UpgradeType.Zerg_Flyer_Carapace.maxRepeats(), Delegate { upgrade(UpgradeType.Zerg_Flyer_Carapace) })
            )

    private fun considerBaseDefense(): Repeat<Any> {
        return Repeat(child = fallback(
                sequence(
                        DispatchParallel<Any, Base>("ConsiderBaseDefenders", { MyInfo.myBases })
                        {
                            val base = it as PlayerUnit
                            sequence(
                                    Combat.shouldIncreaseDefense(base.position),
                                    parallel(1000,
                                            Repeat(child = Delegate {
                                                val enemies = Cluster.enemyClusters.minBy { base.getDistance(it.position) }
                                                        ?: return@Delegate Fail
                                                val flyers = enemies.units.count { it is Attacker && it.isFlying }
                                                val ground = enemies.units.count { it is Attacker && !it.isFlying }
                                                if (ground > flyers)
                                                    build(UnitType.Zerg_Sunken_Colony, false, { base.tilePosition })
                                                else
                                                    build(UnitType.Zerg_Spore_Colony, false, { base.tilePosition })
                                            }),
                                            Repeat(child = train(UnitType.Zerg_Zergling, { base.tilePosition }))
                                    )
                            )
                        }),
                Sleep
        ))
    }
}