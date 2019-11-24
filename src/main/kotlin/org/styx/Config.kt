package org.styx

object Config {
    val logEnabled: Boolean = true
    val attackerHysteresisValue = 37
    val retreatHysteresisFrames = 24
    val simHorizon = 6 * 24
    val evalFramesToTurnFactor = 1 / 7.0
    val evalFramesToReach = 1 / 20.0
    val evalFramesToKillFactor = 1 / 1000.0
    val productionHysteresisFrames = 48
    val waitForReinforcementsTargetingFactor = 0.4
}