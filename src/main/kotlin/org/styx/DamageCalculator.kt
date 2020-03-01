package org.styx

import bwapi.DamageType
import bwapi.UnitSizeType
import kotlin.math.max

object DamageCalculator {
    fun damageToShields(damage: Int, shieldUpgrades: Int = 0) =
            max(0, damage - shieldUpgrades)

    fun damageToHitpoints(
            damagePerHit: Int,
            hits: Int = 1,
            damageType: DamageType,
            targetArmor: Int = 0,
            targetSize: UnitSizeType): Int {
        val afterArmor = max(0, (damagePerHit - targetArmor)) * hits
        val damageDivisor = when (damageType) {
            DamageType.Concussive -> when (targetSize) {
                UnitSizeType.Medium -> 2
                UnitSizeType.Large -> 4
                else -> 1
            }
            DamageType.Explosive -> when (targetSize) {
                UnitSizeType.Small -> 2
                UnitSizeType.Medium -> 4
                else -> 1
            }
            else -> 1
        }
        return afterArmor / damageDivisor
    }
}