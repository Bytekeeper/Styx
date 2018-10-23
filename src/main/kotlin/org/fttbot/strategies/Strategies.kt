package org.fttbot.strategies

import org.fttbot.task.Build
import org.fttbot.task.Expand
import org.fttbot.task.HaveBuilding
import org.fttbot.task.Train
import org.openbw.bwapi4j.type.UnitType

object Strategies {
    val TWO_HATCH_MUTA = listOf(
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Overlord),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            HaveBuilding(UnitType.Zerg_Spawning_Pool),
            Build(UnitType.Zerg_Extractor),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Expand(),
            HaveBuilding(UnitType.Zerg_Lair),
            Train(UnitType.Zerg_Zergling),
            Train(UnitType.Zerg_Zergling),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Build(UnitType.Zerg_Spire),
            Train(UnitType.Zerg_Drone)
    )

    val ZERGLING_MADNESS = listOf(
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Overlord),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            Train(UnitType.Zerg_Drone),
            HaveBuilding(UnitType.Zerg_Spawning_Pool),
            Train(UnitType.Zerg_Zergling).nvr()
    )
}