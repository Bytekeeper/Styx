package org.fttbot.strategies

import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MSequence.Companion.msequence
import org.fttbot.Parallel.Companion.parallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.info.*
import org.fttbot.task.Production
import org.fttbot.task.Production.cancelGas
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*

object Strategies {
    fun considerCanceling() = Repeat(child = sequence(
            DispatchParallel("Cancel buildings?", { UnitQuery.myUnits.filter { it is Building && !it.isCompleted } }) {
                fallback(considerCancelling(it), Sleep)
            },
            Sleep
    ))

    private fun considerCancelling(it: PlayerUnit): Node =
            sequence(
                    Condition("Won't survive?") {
                        it as Building
                        val mySim = SimUnit.of(it)
                        val dpf = UnitQuery.enemyUnits.inRadius(it, 200)
                                .filter { enemy -> enemy.canAttack(it, 16) }
                                .sumByDouble { SimUnit.of(it).damagePerFrameTo(mySim) } + 0.01
                        it.isUnderAttack && it.remainingBuildTime > (it.hitPoints + it.shields) / dpf - 12
                    },
                    Delegate { CancelCommand(it as Building) }
            )

    fun considerBuildingWorkers() = Repeat(child = fallback(
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
            fallback(Condition("Has larvae?") { UnitQuery.myUnits.any { it is Larva } }, Sleep),
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
                DispatchParallel("ConsiderBaseDefenders", { MyInfo.myBases }) {
                    val base = it as PlayerUnit
                    var ground = 0
                    var air = 0
                    Repeat(child = sequence(
                            Inline("Determine type of defense") {
                                val relevantUnits = UnitQuery.ownedUnits.filter {
                                    it is Attacker &&
                                            (it is MobileUnit && it.getDistance(base) < 1800 || it.getDistance(base) < 300)
                                }
                                val enemies = relevantUnits.filter { it.isEnemyUnit }
                                        .map { val res = SimUnit.of(it); res.position = null; res }
                                val myUnits = relevantUnits.filter { it.isMyUnit && it !is Worker && it.getDistance(base) < 1200 }
                                air = CombatEval.minAmountOfAdditionalsForProbability(
                                        myUnits.map { val u = SimUnit.of(it); u.position = null; u  },
                                        SimUnit.of(UnitType.Zerg_Spore_Colony),
                                        enemies
                                )
                                ground = CombatEval.minAmountOfAdditionalsForProbability(
                                        myUnits.map { val u = SimUnit.of(it); u.position = null; u },
                                        SimUnit.Companion.of(UnitType.Zerg_Sunken_Colony),
                                        enemies
                                )
                                if (air <= 0 && ground <= 0)
                                    NodeStatus.RUNNING
                                else
                                    NodeStatus.SUCCEEDED
                            },
                            parallel(2,
                                    fallback(
                                            Condition("Air defense not needed?") { air <= 0 },
                                            DispatchParallel("Build spores", { (1..air).toList() }) {
                                                Production.build(UnitType.Zerg_Spore_Colony, false, { base.tilePosition })
                                            }
                                    ),
                                    fallback(
                                            Condition("Ground defense not needed?") { ground <= 0 },
                                            parallel(2,
                                                    DispatchParallel("Build sunkens", { (1..ground).toList() }) {
                                                        Production.build(UnitType.Zerg_Sunken_Colony, false, { base.tilePosition })
                                                    },
                                                    Production.train(UnitType.Zerg_Zergling, { base.tilePosition })
                                            )
                                    )
                            )
                    ))
                },
                Sleep
        ))
    }
}