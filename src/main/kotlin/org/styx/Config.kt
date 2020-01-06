package org.styx

import java.util.logging.Level

object Config {
    val minLevel = Level.INFO
    const val logEnabled: Boolean = true
    const val attackerHysteresisFactor = 0.9
    const val mediumSimHorizon = 8 * 24
    const val longSimHorizon = 12 * 24
    const val veryLongSimHorizon = 16 * 24
    const val evalFramesToReach = 1 / 12.0
    const val evalFramesToKillFactor = 1 / 500.0
    const val productionHysteresisFrames = 60
    const val waitForReinforcementsTargetingFactor = 0.6

    const val dpfThreatValueFactor = 77
    const val rangeValueFactor = 1/90.0
    const val splashValueFactor = 1.5
}