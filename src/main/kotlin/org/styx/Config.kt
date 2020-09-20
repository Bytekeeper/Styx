package org.styx

import java.util.logging.Level

object Config {
    val minLevel = Level.WARNING
    const val logEnabled: Boolean = true
    const val attackerHysteresisFactor = 0.85
    const val shortSimHorizon = 5 * 24
    const val mediumSimHorizon = 10 * 24
    const val veryLongSimHorizon = 15 * 24
    const val productionHysteresisFrames = 60
    const val waitForReinforcementsTargetingFactor = 0.2
    const val seekSquadEvalFactor = 9.0

    const val dpfThreatValueFactor = 77
    const val rangeValueFactor = 1 / 200.0
    const val splashValueFactor = 1.5

    object TargetEval {
        const val evalFramesToReach = 1 / 5.0
        const val evalFramesToKillFactor = 1 / 300.0
        const val enemySpeedFactor = 1 / 220.0
        const val combatRelevancyFactor = 15.0
        const val pylonFactor = 0.3
    }
}