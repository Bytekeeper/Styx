package org.styx

import java.util.logging.Level

object Config {
    val minLevel = Level.WARNING
    const val logEnabled: Boolean = false
    const val attackerHysteresisFactor = 0.8
    const val mediumSimHorizon = 8 * 24
    const val longSimHorizon = 12 * 24
    const val veryLongSimHorizon = 16 * 24
    const val evalFramesToReach = 1 / 15.0
    const val evalFramesToKillFactor = 1 / 300.0
    const val productionHysteresisFrames = 60
    const val waitForReinforcementsTargetingFactor = 0.6

    const val dpfThreatValueFactor = 77
    const val rangeValueFactor = 1/300.0
    const val splashValueFactor = 1.5
}