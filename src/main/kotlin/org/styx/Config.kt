package org.styx

import java.util.logging.Level

object Config {
    val minLevel = Level.INFO
    const val logEnabled: Boolean = true
    const val attackerHysteresisFactor = 0.25
    const val mediumSimHorizon = 6 * 24
    const val longSimHorizon = 10 * 24
    const val veryLongSimHorizon = 18 * 24
    const val evalFramesToTurnFactor = 1 / 5.0
    const val evalFramesToReach = 1 / 15.0
    const val evalFramesToKillFactor = 1 / 500.0
    const val productionHysteresisFrames = 60
    const val waitForReinforcementsTargetingFactor = 0.6

    const val dpfThreatValueFactor = 77
    const val rangeValueFactor = 1/80.0
}