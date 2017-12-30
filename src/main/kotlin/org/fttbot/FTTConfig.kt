package org.fttbot

import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.UnitType

object FTTConfig {
    lateinit var MY_RACE: Race
    lateinit var BASE: UnitType
    lateinit var WORKER: UnitType
    lateinit var SUPPLY: UnitType
    lateinit var GAS_BUILDING: UnitType

    fun useConfigForTerran() {
        MY_RACE = Race.Terran
        BASE = UnitType.Terran_Command_Center
        WORKER = UnitType.Terran_SCV
        SUPPLY = UnitType.Terran_Supply_Depot
        GAS_BUILDING = UnitType.Terran_Refinery
    }

    fun useConfigForProtoss() {
        MY_RACE = Race.Protoss
        BASE = UnitType.Protoss_Nexus
        WORKER = UnitType.Protoss_Probe
        SUPPLY = UnitType.Protoss_Pylon
        GAS_BUILDING = UnitType.Protoss_Assimilator
    }

    fun useConfigForZerg() {
        MY_RACE = Race.Zerg
        BASE = UnitType.Zerg_Hatchery
        WORKER = UnitType.Zerg_Drone
        SUPPLY = UnitType.Zerg_Overlord
        GAS_BUILDING = UnitType.Zerg_Extractor
    }
}