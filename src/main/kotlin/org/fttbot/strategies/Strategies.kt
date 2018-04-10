package org.fttbot.strategies

import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MSequence.Companion.msequence
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.estimation.SimUnit
import org.fttbot.info.*
import org.fttbot.task.Production
import org.fttbot.task.Production.cancelGas
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.Egg
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker

object Strategies {
    fun considerCanceling() = fallback(
            sequence(
                    DispatchParallel<Any, PlayerUnit>("Cancel buildings?", { UnitQuery.myUnits.filter { it is Building && !it.isCompleted } }) {
                        fallback(
                                sequence(
                                        Condition("Won't survive?") {
                                            it as Building
                                            val mySim = SimUnit.of(it)
                                            val dpf = UnitQuery.enemyUnits.inRadius(it, 200)
                                                    .filter { it.canAttack(it, 16) }
                                                    .sumByDouble { SimUnit.of(it).damagePerFrameTo(mySim) }
                                            it.remainingBuildTime > (it.hitPoints + it.shields) / dpf - 24 * 2
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
                            Production.trainWorker(),
                            Fail)
                    , Sleep)

    private fun workerMineralDelta(base: PlayerUnit) =
            2 * UnitQuery.minerals.count { m -> m.getDistance(base.position) < 300 } -
                    UnitQuery.myWorkers.count { w -> w.getDistance(base.position) < 300 }

    fun gasTrick() = msequence("gasTrick",
            Production.buildGas(),
            Production.trainWorker(),
            cancelGas()
    )

}