package org.styx

import java.util.logging.Level

object Config {
    val minLevel = Level.WARNING
    val logEnabled: Boolean = false
    val attackerHysteresisValue = 19
    val simHorizon = 6 * 24
    val fleeSimHorizon = 10 * 24
    val evalFramesToTurnFactor = 1 / 5.0
    val evalFramesToReach = 1 / 17.0
    val evalFramesToKillFactor = 1 / 900.0
    val productionHysteresisFrames = 48
    val waitForReinforcementsTargetingFactor = 0.4
    const val dpfThreatValueFactor = 37
}