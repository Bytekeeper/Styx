package org.styx

import java.util.logging.Level

object Config {
    val minLevel = Level.WARNING
    const val logEnabled: Boolean = false
    const val attackerHysteresisFactor = 0.8
    const val mediumSimHorizon = 17 * 12
    const val veryLongSimHorizon = 32 * 12
    const val productionHysteresisFrames = 60
    const val waitForReinforcementsTargetingFactor = 0.4
    const val seekSquadEvalFactor = 2800.0

    const val dpfThreatValueFactor = 77
    const val rangeValueFactor = 1 / 200.0
    const val splashValueFactor = 1.5

    object TargetEval {
        const val evalFramesToReach = 1 / 20.0
        const val evalFramesToKillFactor = 1 / 180.0
        const val enemySpeedFactor = 1 / 180.0
        const val combatRelevancyFactor = 1 / 1.5
        const val pylonFactor = 0.4
    }
}