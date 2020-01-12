package org.styx

import bwapi.Player
import bwapi.TilePosition
import bwapi.UpgradeType
import org.bk.ass.manage.GMS
import org.styx.Styx.resources

fun Iterable<Double>.min(default: Double): Double = min() ?: default
fun Iterable<Int>.min(default: Int): Int = min() ?: default


operator fun TilePosition.div(factor: Int) = divide(factor)
fun Player.canUpgrade(upgradeType: UpgradeType): Boolean {
    val currentLevel = getUpgradeLevel(upgradeType)
    return !isUpgrading(upgradeType)
            && currentLevel < getMaxUpgradeLevel(upgradeType)
            && resources.availableGMS.canAfford(GMS.upgradeCost(upgradeType, currentLevel + 1))
            && Styx.units.myCompleted(upgradeType.whatsRequired(currentLevel + 1)).isNotEmpty()
            && Styx.units.myCompleted(upgradeType.whatUpgrades()).isNotEmpty()
}