package org.styx.task

import bwapi.UnitType
import bwapi.UpgradeType
import org.styx.*
import org.styx.macro.*

object FollowBO : Par("Follow Build Order",
        Get(9, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Train(UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Extractor),
        Train(UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Hydralisk_Den),
        Train(UnitType.Zerg_Overlord),
        Morph(UnitType.Zerg_Lair),
        Repeat(delegate = Get(6, UnitType.Zerg_Zergling)),
        Repeat(delegate = Seq("Build supply", Condition { Styx.self.supplyTotal() - Styx.self.supplyUsed() < 6 }, Train(UnitType.Zerg_Overlord))),
        Repeat(delegate = Get(6, UnitType.Zerg_Hydralisk)),
        Upgrade(UpgradeType.Muscular_Augments, 1),
        Upgrade(UpgradeType.Grooved_Spines, 1),
        Get(80, UnitType.Zerg_Hydralisk)
)