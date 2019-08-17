package org.styx.task

import bwapi.UnitType
import org.styx.*
import org.styx.Styx.bases
import org.styx.Styx.buildPlan
import org.styx.Styx.units
import org.styx.macro.Build
import org.styx.macro.Get
import org.styx.macro.Train
import kotlin.math.min

object FollowBO : Par("Follow Build Order",
        Get(9, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Repeat(delegate = Get(6, UnitType.Zerg_Zergling)),
        Repeat(delegate = Seq("Supply", Condition {
            Styx.self.supplyTotal() - Styx.self.supplyUsed() + buildPlan.pendingUnits.sumBy { it.supplyProvided() } +
                    units.mine.filter { !it.completed }.sumBy { it.unitType.supplyProvided() + it.buildType.supplyProvided() } < 6
        }, Train(UnitType.Zerg_Overlord))),
        Repeat(delegate = Seq("Train workers", Condition {
            min(units.mine.count() / 5, bases.myBases.sumBy { b ->
                units.minerals.inRadius(b.center, 400).size * 3 / 2 + units.resourceDepots.inRadius(b.center, 400).size * 4
            }) > units.workers.size + buildPlan.pendingUnits.count { it.isWorker }
        }, Train(UnitType.Zerg_Drone))),
        Get(2, UnitType.Zerg_Hatchery),
        Repeat(delegate = Seq("Macrohatch", Condition {
            Styx.self.minerals() > 400 && buildPlan.pendingUnits.none { it == UnitType.Zerg_Hatchery }
        }, Build(UnitType.Zerg_Hatchery))),
        Get(200, UnitType.Zerg_Zergling)
)
