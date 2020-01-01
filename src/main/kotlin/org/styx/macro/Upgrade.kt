package org.styx.macro

import bwapi.UpgradeType
import org.bk.ass.bt.NodeStatus
import org.styx.*

class Upgrade(private val upgrade: UpgradeType, private val level: Int) : MemoLeaf() {
    private val costLock = costLocks.upgradeCostLock(upgrade, level)
    private val researcherLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == upgrade.whatUpgrades() } }

    override fun tick(): NodeStatus {
        if (upgradeIsDone())
            return NodeStatus.SUCCESS
        if (isAlreadyUpgrading())
            return NodeStatus.RUNNING
        costLock.acquire()
        if (costLock.isSatisfied) {
            researcherLock.acquire()
            val researcher = researcherLock.item ?: return NodeStatus.RUNNING
            researcher.upgrade(upgrade)
        }
        return NodeStatus.RUNNING
    }

    private fun upgradeIsDone() = Styx.self.getUpgradeLevel(upgrade) == level

    private fun isAlreadyUpgrading() = Styx.self.isUpgrading(upgrade) && Styx.self.getUpgradeLevel(upgrade) == level - 1
}