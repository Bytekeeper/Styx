package org.styx

object Config {
    val logEnabled: Boolean = false
    val attackerHysteresisValue = 16
    val retreatHysteresisFrames = 24
    val simHorizon = 6 * 24
    val evalFramesToTurnFactor = 1 / 5.0
    val evalFramesToReach = 1 / 17.0
    val evalFramesToKillFactor = 1 / 800.0
    val productionHysteresisFrames = 48
    val waitForReinforcementsTargetingFactor = 0.4
}