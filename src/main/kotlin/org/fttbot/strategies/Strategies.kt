package org.fttbot.strategies

import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MSequence.Companion.msequence
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.MyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.info.isReadyForResources
import org.fttbot.task.Production
import org.fttbot.task.Production.cancelGas
import org.openbw.bwapi4j.unit.Egg
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker

object Strategies {
    fun considerWorkers() =
            fallback(
                    sequence(
                            Condition("should build workers") {
                                UnitQuery.myUnits.count { it is Worker && !it.isCompleted || it is Egg && it.buildType.isWorker } <
                                        MyInfo.myBases.count {
                                            it as PlayerUnit
                                            it.isReadyForResources &&
                                                    workerMineralDelta(it) > 0
                                        } - FTTBot.self.minerals() / 1000 - FTTBot.self.gas() / 500
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