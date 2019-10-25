package org.styx.task

import bwapi.UnitType
import bwapi.UpgradeType
import org.styx.*
import org.styx.Styx.bases
import org.styx.Styx.buildPlan
import org.styx.Styx.economy
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.macro.Build
import org.styx.macro.Get
import org.styx.macro.Train
import org.styx.macro.Upgrade
import kotlin.math.min

object FollowBO : Par("Follow Build Order",
        Get(9, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Repeat(delegate = Get(6, UnitType.Zerg_Zergling)),
        Upgrade(UpgradeType.Metabolic_Boost, 1),
        Repeat(delegate = Seq("Supply", Condition {
             economy.supplyWithPlanned  < 4
        }, Train(UnitType.Zerg_Overlord))),
        Repeat(delegate = Seq("Train workers", Condition {
            min(units.mine.count() / 5, bases.myBases.sumBy { b ->
                units.minerals.inRadius(b.center, 400).size * 3 / 2 + units.resourceDepots.inRadius(b.center, 400).size * 4
            }) > units.workers.size + buildPlan.plannedUnits.count { it.isWorker }
        }, Train(UnitType.Zerg_Drone))),
        Get(2, UnitType.Zerg_Hatchery),
        Repeat(delegate = Seq("Macrohatch", Condition {
            resources.availableGMS.minerals > 300 + (buildPlan.plannedUnits.count { it.isResourceDepot } + units.resourceDepots.count { it.completed }) * 100
        }, Build(UnitType.Zerg_Hatchery))),
        Get(200, UnitType.Zerg_Zergling)
)
