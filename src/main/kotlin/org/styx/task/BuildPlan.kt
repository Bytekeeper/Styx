package org.styx.task

import bwapi.UnitType
import bwapi.UpgradeType
import org.styx.*
import org.styx.Styx.bases
import org.styx.Styx.economy
import org.styx.Styx.units
import org.styx.macro.*


object Nine734 : Par("9734",
        Get(9, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Build(UnitType.Zerg_Spawning_Pool),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Expand(),
        Train(UnitType.Zerg_Zergling),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Expand(),
        Build(UnitType.Zerg_Extractor),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Hydralisk_Den),
        Train(UnitType.Zerg_Overlord),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Upgrade(UpgradeType.Muscular_Augments, 1),
        Train(UnitType.Zerg_Zergling),
        Train(UnitType.Zerg_Zergling),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Drone),
        ensureSupply(),
        Get(200, UnitType.Zerg_Hydralisk),
        Upgrade(UpgradeType.Grooved_Spines, 1),
        Expand(),
        Gathering()
)

object TwelveHatch : Par("Blub",
        Get(9, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Train(UnitType.Zerg_Drone),
        ExtractorTrick(),
        Train(UnitType.Zerg_Overlord),
        Repeat(delegate = Seq("Supply", Condition {
            economy.supplyWithPlanned < 3
        }, Train(UnitType.Zerg_Overlord))),
        repeat("Initial lings", 3) { Train(UnitType.Zerg_Zergling) },
        Expand(),
        haveBasicZerglingSquad(),
        Build(UnitType.Zerg_Extractor),
        Repeat(delegate = Get(200, UnitType.Zerg_Hydralisk)),
//        Repeat(delegate = Seq("Train workers", Condition {
//            min(FollowBO.workerAmountBaseOnSupply(), FollowBO.workerAmountRequiredToFullyUtilize()) > units.workers.size + buildPlan.plannedUnits.count { it.isWorker }
//        }, Train(UnitType.Zerg_Drone))),
        Gathering()
)

object NinePoolCheese : Par("9 Pool Cheese",
        Get(9, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        ensureSupply(),
        repeat("Initial lings", 3) { Train(UnitType.Zerg_Zergling) },
        haveBasicZerglingSquad(),
        Repeat(delegate = Get(7, UnitType.Zerg_Drone)),
        pumpLings(),
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

private fun pumpLings() = Repeat(delegate = Get(200, UnitType.Zerg_Zergling))
private fun haveBasicZerglingSquad() = Repeat(delegate = Get(6, UnitType.Zerg_Zergling))
private fun ensureSupply() = Repeat(delegate = Seq("Supply", Condition {
    economy.supplyWithPlanned < 4
}, Train(UnitType.Zerg_Overlord)))