package org.fttbot.strategies

import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MSequence.Companion.msequence
import org.fttbot.Parallel.Companion.parallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.info.*
import org.fttbot.task.Combat
import org.fttbot.task.Production
import org.fttbot.task.Production.cancelGas
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*
import sun.security.provider.Sun

object Strategies {
    fun considerCanceling() = fallback(
            sequence(
                    DispatchParallel("Cancel buildings?", { UnitQuery.myUnits.filter { it is Building && !it.isCompleted } }) {
                        fallback(
                                sequence(
                                        Condition("Won't survive?") {
                                            it as Building
                                            val mySim = SimUnit.of(it)
                                            val dpf = UnitQuery.enemyUnits.inRadius(it, 200)
                                                    .filter { enemy -> enemy.canAttack(it, 16) }
                                                    .sumByDouble { SimUnit.of(it).damagePerFrameTo(mySim) } + 0.01
                                            it.remainingBuildTime > (it.hitPoints + it.shields) / dpf - 12
                                        },
                                        Delegate { CancelCommand(it as Building) }
                                ),
                                Sleep
                        )
                    },
                    Sleep
            ),
            Sleep
    )

    fun considerWorkers() =
            Repeat(child =
            fallback(
                    sequence(
                            Condition("should build workers") {
                                UnitQuery.myUnits.count { it is Worker && !it.isCompleted || it is Egg && it.buildType.isWorker } <
                                        MyInfo.myBases.sumBy {
                                            it as PlayerUnit
                                            if (it.isReadyForResources)
                                                workerMineralDelta(it) / 5
                                            else
                                                0
                                        } - FTTBot.self.minerals() / 3000 - FTTBot.self.gas() / 1500
                            },
                            Production.trainWorker())
                    , Sleep)
            )

    private fun workerMineralDelta(base: PlayerUnit) =
            2 * UnitQuery.minerals.count { m -> m.getDistance(base.position) < 300 } -
                    UnitQuery.myWorkers.count { w -> w.getDistance(base.position) < 300 }

    fun gasTrick() = msequence("gasTrick",
            Production.buildGas(),
            Production.trainWorker(),
            cancelGas()
    )

    fun considerMoreTrainers(): Repeat {
        return Repeat(child = fallback(
                sequence(
                        Condition("Too much money?") { Board.resources.minerals > MyInfo.myBases.size * 200 },
                        Production.build(UnitType.Zerg_Hatchery)
                ),
                Sleep
        ))
    }

    fun considerBaseDefense(): Repeat {
        return Repeat(child = fallback(
                sequence(
                        DispatchParallel("ConsiderBaseDefenders", { MyInfo.myBases })
                        {
                            val base = it as PlayerUnit
                            var ground = 0
                            var air = 0
                            sequence(
                                    fallback(Combat.shouldIncreaseDefense(base.position), Sleep),
                                    Repeat(child = sequence(
                                            Inline("Determine type of defense") {
                                                val enemies = Cluster.enemyClusters.filter {
                                                    it.position.getDistance(base.position) < 2000 &&
                                                            it.units.any { it is MobileUnit && it is Attacker }
                                                }.flatMap { it.units }.map { val res = SimUnit.of(it); res.position = null; res }
                                                val myUnits = UnitQuery.myUnits.filter { it.getDistance(base) < 300}
                                                air = CombatEval.minAmountOfAdditionalsForProbability(
                                                        myUnits.filter { it is SporeColony }.map { SimUnit.of(it) },
                                                        SimUnit.of(UnitType.Zerg_Spore_Colony),
                                                        enemies
                                                )
                                                ground = CombatEval.minAmountOfAdditionalsForProbability(
                                                        myUnits.filter { it is SunkenColony }.map { SimUnit.of(it) },
                                                        SimUnit.Companion.of(UnitType.Zerg_Sunken_Colony),
                                                        enemies
                                                )
                                                NodeStatus.SUCCEEDED
                                            },
                                            fallback(
                                                    Condition("Ground defense not needed?") { ground <= 0 },
                                                    parallel(2,
                                                            DispatchParallel("Build sunkens", {(1..ground).toList()}) {
                                                                Production.build(UnitType.Zerg_Sunken_Colony, false, { base.tilePosition })
                                                            },
                                                            Production.train(UnitType.Zerg_Zergling, { base.tilePosition })
                                                    )
                                            ),
                                            fallback(
                                                    Condition("Air defense not needed?") { air <= 0 },
                                                    DispatchParallel("Build spores", {(1..air).toList()}) {
                                                        Production.build(UnitType.Zerg_Spore_Colony, false, { base.tilePosition })
                                                    }
                                            )
                                    ))
                            )
                        }),
                Sleep
        ))
    }
}