package org.fttbot.sim

import org.fttbot.FTTBot
import org.fttbot.estimation.SimUnit
import org.openbw.bwapi4j.unit.PlayerUnit

data class WorldState(val units: List<SimUnit>) {
    companion object {
        fun fromBW() = WorldState(
                FTTBot.game.allUnits.filterIsInstance(PlayerUnit::class.java).map { SimUnit.Companion.of(it) }
        )
    }
}