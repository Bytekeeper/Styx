package org.styx

import bwapi.Player
import bwapi.Position
import bwapi.UnitType
import bwapi.UpgradeType
import org.bk.ass.sim.Agent

data class PendingUnit(
        val trainer: SUnit,
        val position: Position,
        val unitType: UnitType,
        val remainingFrames: Int)


class PendingUpgrades {
    fun isUpgradingSpeed(unitType: UnitType, player: Player): Boolean =
            when (unitType) {
                UnitType.Zerg_Zergling -> player.isUpgrading(UpgradeType.Metabolic_Boost)
                UnitType.Zerg_Hydralisk -> player.isUpgrading(UpgradeType.Muscular_Augments)
                UnitType.Zerg_Overlord -> player.isUpgrading(UpgradeType.Pneumatized_Carapace)
                UnitType.Zerg_Ultralisk -> player.isUpgrading(UpgradeType.Anabolic_Synthesis)
                UnitType.Protoss_Shuttle -> player.isUpgrading(UpgradeType.Gravitic_Thrusters)
                UnitType.Protoss_Observer -> player.isUpgrading(UpgradeType.Gravitic_Boosters)
                UnitType.Protoss_Zealot -> player.isUpgrading(UpgradeType.Leg_Enhancements)
                UnitType.Terran_Vulture -> player.isUpgrading(UpgradeType.Ion_Thrusters)
                else -> false
            }

    fun agentWithPendingUpgradesApplied(agent: Agent): Agent {
        val unit = agent.userObject as SUnit
        if (isUpgradingSpeed(unit.unitType, Styx.self)) {
            agent.setSpeedUpgrade(true)
        }
        return agent
    }
}