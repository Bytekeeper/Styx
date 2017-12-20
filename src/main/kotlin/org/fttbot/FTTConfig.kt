package org.fttbot

import bwapi.Race
import org.fttbot.layer.FUnitType

object FTTConfig {
    lateinit var MY_RACE: Race
    lateinit var BASE: FUnitType
    lateinit var WORKER: FUnitType
    lateinit var SUPPLY: FUnitType
    lateinit var GAS_BUILDING: FUnitType

    fun useConfigForTerran() {
        MY_RACE = Race.Terran
        BASE = FUnitType.Terran_Command_Center
        WORKER = FUnitType.Terran_SCV
        SUPPLY = FUnitType.Terran_Supply_Depot
        GAS_BUILDING = FUnitType.Terran_Refinery
    }

    fun useConfigForProtoss() {
        MY_RACE = Race.Protoss
        BASE = FUnitType.Protoss_Nexus
        WORKER = FUnitType.Protoss_Probe
        SUPPLY = FUnitType.Protoss_Pylon
        GAS_BUILDING = FUnitType.Protoss_Assimilator
    }

    fun useConfigForZerg() {
        MY_RACE = Race.Zerg
        BASE = FUnitType.Zerg_Hatchery
        WORKER = FUnitType.Zerg_Drone
        SUPPLY = FUnitType.Zerg_Overlord
        GAS_BUILDING = FUnitType.Zerg_Extractor
    }
}