package org.styx.global

import bwapi.UpgradeType
import org.styx.Styx

class Tech {
    private lateinit var remainingUpgradeTime: Map<UpgradeType, Int>

    fun update() {
        remainingUpgradeTime = Styx.units.mine
                .filter { it.unit.isUpgrading }
                .map { it.unit.upgrade to it.unit.remainingUpgradeTime }
                .toMap()
    }

    fun timeRemainingForUpgrade(upgradeType: UpgradeType): Int? = remainingUpgradeTime[upgradeType]
}