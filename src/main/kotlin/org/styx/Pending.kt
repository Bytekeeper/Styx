package org.styx

import bwapi.Player
import bwapi.Position
import bwapi.UnitType
import bwapi.UpgradeType
import org.bk.ass.sim.Agent
import org.styx.Styx.tech

data class PendingUnit(
        val trainer: SUnit,
        val position: Position,
        val unitType: UnitType,
        val remainingFrames: Int)


class PendingUpgrades {
    fun isUpgradingSpeed(unitType: UnitType, player: Player, frames: Int): Boolean = player == Styx.self &&
            tech.timeRemainingForUpgrade(when (unitType) {
                UnitType.Zerg_Zergling -> UpgradeType.Metabolic_Boost
                UnitType.Zerg_Hydralisk -> UpgradeType.Muscular_Augments
                UnitType.Zerg_Overlord -> UpgradeType.Pneumatized_Carapace
                UnitType.Zerg_Ultralisk -> UpgradeType.Anabolic_Synthesis
                UnitType.Protoss_Shuttle -> UpgradeType.Gravitic_Thrusters
                UnitType.Protoss_Observer -> UpgradeType.Gravitic_Boosters
                UnitType.Protoss_Zealot -> UpgradeType.Leg_Enhancements
                UnitType.Terran_Vulture -> UpgradeType.Ion_Thrusters
                else -> UpgradeType.None
            }) ?: Int.MAX_VALUE < frames

    fun agentWithPendingUpgradesApplied(agent: Agent, frames: Int): Agent {
        val unit = agent.userObject as SUnit
        if (isUpgradingSpeed(unit.unitType, Styx.self, frames)) {
            agent.setSpeedUpgrade(true)
        }
        return agent
    }
}