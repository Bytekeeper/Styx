package org.fttbot.strategies

import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MSequence.Companion.msequence
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.estimation.SimUnit
import org.fttbot.info.*
import org.fttbot.task.Combat
import org.fttbot.task.Production
import org.fttbot.task.Production.cancelGas
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*

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
                                            it.remainingBuildTime > (it.hitPoints + it.shields) / dpf - 18
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
                            sequence(
                                    Combat.shouldIncreaseDefense(base.position),
                                    Parallel.parallel(1000,
                                            Repeat(child = Delegate {
                                                val enemies = Cluster.enemyClusters.minBy { base.getDistance(it.position) }
                                                        ?: return@Delegate Fail
                                                val flyers = enemies.units.count { it is Attacker && it.isFlying }
                                                val ground = enemies.units.count { it is Attacker && !it.isFlying }
                                                if (ground > flyers)
                                                    Production.build(UnitType.Zerg_Sunken_Colony, false, { base.tilePosition })
                                                else
                                                    Production.build(UnitType.Zerg_Spore_Colony, false, { base.tilePosition })
                                            }),
                                            Repeat(child = Production.train(UnitType.Zerg_Zergling, { base.tilePosition }))
                                    )
                            )
                        }),
                Sleep
        ))
    }
}