package org.fttbot.estimation

import bwapi.DamageType
import bwapi.Position
import bwapi.TilePosition
import bwapi.UnitSizeType
import org.fttbot.import.FUnitType
import org.fttbot.import.FWeaponType
import org.fttbot.layer.UnitLike
import org.fttbot.layer.getWeaponAgainst
import org.fttbot.layer.isMelee
import kotlin.math.exp
import kotlin.math.max

const val MAX_FRAMES_TO_ATTACK = 25
const val HEAL_PER_ENERGY = 2

object CombatEval {
    fun probabilityToWin(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>): Double {
        val damageA = unitsOfPlayerA.sumByDouble { a ->
            if (a.type == FUnitType.Terran_Bunker) return FUnitType.Terran_Marine.groundWeapon.damageAmount * 4.0
            else unitsOfPlayerB.map { b -> a.damagePerFrameTo(b) }.max() ?: 0.01
        }
        val damageB = unitsOfPlayerB.sumByDouble { b ->
            if (b.type == FUnitType.Terran_Bunker) return FUnitType.Terran_Marine.groundWeapon.damageAmount * 4.0
            else unitsOfPlayerA.map { a -> b.damagePerFrameTo(a) }.max() ?: 0.01
        }
        val hpA = unitsOfPlayerA.sumBy { it.hitPoints } + unitsOfPlayerA.count { it.canHeal } * HEAL_PER_ENERGY
        val hpB = unitsOfPlayerB.sumBy { it.hitPoints } + unitsOfPlayerB.count { it.canHeal } * HEAL_PER_ENERGY

        val rangeA = unitsOfPlayerA.map { max(it.type.groundWeapon.maxRange, it.type.airWeapon.maxRange) }.sum() + 0.1
        val rangeB = unitsOfPlayerB.map { max(it.type.groundWeapon.maxRange, it.type.airWeapon.maxRange) }.sum() + 0.1
        val mobilityA = unitsOfPlayerA.map { it.type.topSpeed }.sum() + 0.1
        val mobilityB = unitsOfPlayerB.map { it.type.topSpeed }.sum() + 0.1

        val agg = 5000.0 * (damageA / (hpB.toFloat() + .1) - damageB / (hpA.toFloat() + .1)) +
                20 * (rangeA / mobilityB - rangeB / mobilityA)
        return 1.0 / (exp(-agg) + 1)
    }
}

class SimUnit(override val type: FUnitType,
              val ground: Int = 0,
              override val isUnderDarkSwarm: Boolean = false,
              val airUpgrades: Int = 0,
              val groundUpgrades: Int = 0,
              val armorUpgrades: Int = 0,
              override var hitPoints: Int = type.maxHitPoints,
              override var position: Position? = null,
              override var tilePosition: TilePosition? = null,
              override var canMove: Boolean = type.canMove,
              val canHeal: Boolean = type == FUnitType.Terran_Medic,
              override var airWeaponCooldown: Int = 0,
              override var groundWeaponCooldown: Int = 0,
              override val groundHeight: Int = 0,
              override val isAir: Boolean = type.isFlyer,
              var framesPaused: Int = 0) : UnitLike {
    override val isVisible = false

    companion object {
        fun of(unit: UnitLike): SimUnit = SimUnit(unit.type,
                ground = unit.groundHeight,
                isUnderDarkSwarm = unit.isUnderDarkSwarm,
                hitPoints = unit.hitPoints,
                position = unit.position,
                tilePosition = unit.tilePosition,
                airWeaponCooldown = unit.airWeaponCooldown,
                groundWeaponCooldown = unit.groundWeaponCooldown,
                groundHeight = unit.groundHeight,
                canMove = unit.canMove,
                isAir = unit.isAir)
    }

    /**
     * http://wiki.teamliquid.net/starcraft/Damage
     */
    fun directDamage(other: SimUnit): Double {
        if (other.isUnderDarkSwarm) {
            return 0.0
        }
        val weapon = determineWeaponAgainst(other)
        if (weapon == FWeaponType.None) return 0.0
        val weaponUpgrades = if (other.type.isFlyer) airUpgrades else groundUpgrades
        val damagePerHit = with(weapon) { (damageAmount + damageBonus * weaponUpgrades) * damageFactor }
        val damageAfterArmor = max(damagePerHit - other.type.armor - other.armorUpgrades, 0)
        val damageAfterSize = max(damageAfterArmor.toDouble() * damageTypeModifier(weapon.damageType, other.type.size), 0.5)
        if (weapon.isMelee()) {
            return damageAfterSize
        }
        if (ground < other.ground) {
            return damageAfterSize * 0.53125
        }
        return damageAfterSize * 0.99609375
    }

    fun damagePerFrameTo(other: SimUnit): Double {
        val weapon = determineWeaponAgainst(other)
        return if (weapon == FWeaponType.None) 0.0
        else directDamage(other) / weapon.damageCooldown
    }

    fun determineWeaponAgainst(other: SimUnit) = if (type == FUnitType.Terran_Bunker) FUnitType.Terran_Marine.getWeaponAgainst(other)
    else type.getWeaponAgainst(other)

    fun weaponCoolDownVs(other: SimUnit) = if (other.isAir) airWeaponCooldown else groundWeaponCooldown

    private fun damageTypeModifier(damageType: DamageType, unitSizeType: UnitSizeType): Double =
            when (damageType) {
                DamageType.Concussive -> when (unitSizeType) {
                    UnitSizeType.Small -> 1.0
                    UnitSizeType.Medium -> 0.5
                    UnitSizeType.Large -> 0.25
                    else -> throw IllegalStateException("Can't compare against ${unitSizeType}")
                }
                DamageType.Explosive -> when (unitSizeType) {
                    UnitSizeType.Small -> 0.5
                    UnitSizeType.Medium -> 0.75
                    UnitSizeType.Large -> 1.0
                    else -> throw IllegalStateException("Can't compare against ${unitSizeType}")
                }
                DamageType.Normal -> 1.0
                DamageType.Ignore_Armor -> 1.0
                else -> throw IllegalStateException("Can't compare with ${damageType}")
            }

    override fun toString(): String = "{$type} at ${position} with ${hitPoints} hp"
}
