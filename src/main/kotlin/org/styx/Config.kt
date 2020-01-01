package org.styx

import java.util.logging.Level

object Config {
    val minLevel = Level.INFO
    val logEnabled: Boolean = true
    const val attackerHysteresisFactor = 0.25
    val mediumSimHorizon = 6 * 24
    val longSimHorizon = 10 * 24
    val veryLongSimHorizon = 18 * 24
    val evalFramesToTurnFactor = 1 / 5.0
    val evalFramesToReach = 1 / 15.0
    val evalFramesToKillFactor = 1 / 500.0
    val productionHysteresisFrames = 60
    val waitForReinforcementsTargetingFactor = 0.6
    const val dpfThreatValueFactor = 39
}