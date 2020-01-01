package org.styx.global

import bwapi.UpgradeType
import org.bk.ass.bt.TreeNode
import org.styx.Styx

class Tech : TreeNode() {
    private lateinit var remainingUpgradeTime: Map<UpgradeType, Int>

    override fun exec() {
        success()
        remainingUpgradeTime = Styx.units.mine
                .filter { it.unit.isUpgrading }
                .map { it.unit.upgrade to it.unit.remainingUpgradeTime }
                .toMap()
    }

    fun timeRemainingForUpgrade(upgradeType: UpgradeType): Int? = remainingUpgradeTime[upgradeType]
}