package org.styx

import java.util.logging.Level

object Config {
    val minLevel = Level.WARNING
    const val logEnabled: Boolean = false
    const val attackerHysteresisFactor = 0.6
    const val mediumSimHorizon = 17 * 12
    const val longSimHorizon = 24 * 12
    const val veryLongSimHorizon = 32 * 12
    const val evalFramesToReach = 1 / 13.0
    const val evalFramesToKillFactor = 1 / 300.0
    const val productionHysteresisFrames = 60
    const val waitForReinforcementsTargetingFactor = 0.6

    const val dpfThreatValueFactor = 77
    const val rangeValueFactor = 1/200.0
    const val splashValueFactor = 1.5
}