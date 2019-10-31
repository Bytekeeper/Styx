package org.styx.task

import bwapi.UnitType
import bwapi.UpgradeType
import org.styx.*
import org.styx.Styx.bases
import org.styx.Styx.economy
import org.styx.Styx.units
import org.styx.macro.Build
import org.styx.macro.Get
import org.styx.macro.Train
import org.styx.macro.Upgrade

object FollowBO : Par("Follow Build Order",
        Get(9, UnitType.Zerg_Drone),
        Repeat(delegate = Get(1, UnitType.Zerg_Spawning_Pool)),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Repeat(delegate = Seq("Supply", Condition {
            economy.supplyWithPlanned  < 4
        }, Train(UnitType.Zerg_Overlord))),
        Repeat(delegate = Get(200, UnitType.Zerg_Zergling)),
//        Repeat(delegate = Seq("Train workers", Condition {
//            min(FollowBO.workerAmountBaseOnSupply(), FollowBO.workerAmountRequiredToFullyUtilize()) > units.workers.size + buildPlan.plannedUnits.count { it.isWorker }
//        }, Train(UnitType.Zerg_Drone))),
        Get(2, UnitType.Zerg_Hatchery),
        Build(UnitType.Zerg_Extractor),
        Upgrade(UpgradeType.Metabolic_Boost, 1),
        Gathering(true)
) {
    fun workerAmountBaseOnSupply() =
            units.mine.sumBy { it.unitType.supplyRequired() } / 6 + 5
    fun workerAmountRequiredToFullyUtilize() =
            bases.myBases.sumBy { b ->
                units.minerals.inRadius(b.center, 400).size * 3 / 2 + units.myResourceDepots.inRadius(b.center, 400).size * 4
            }
}
