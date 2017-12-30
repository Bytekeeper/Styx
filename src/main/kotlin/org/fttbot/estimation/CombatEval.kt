package org.fttbot.estimation

import org.fttbot.layer.getWeaponAgainst
import org.fttbot.layer.isMelee
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.DamageType
import org.openbw.bwapi4j.type.UnitSizeType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit
import kotlin.math.exp
import kotlin.math.max

const val MAX_FRAMES_TO_ATTACK = 25
const val HEAL_PER_ENERGY = 2

object CombatEval {
    fun probabilityToWin(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>): Double {
        val damageA = unitsOfPlayerA.sumByDouble { a ->
            unitsOfPlayerB.map { b -> a.damagePerFrameTo(b) }.max() ?: 0.01
        }
        val damageB = unitsOfPlayerB.sumByDouble { b ->
            unitsOfPlayerA.map { a -> b.damagePerFrameTo(a) }.max() ?: 0.01
        }
        val hpA = unitsOfPlayerA.sumBy { it.hitPoints } + unitsOfPlayerA.count { it.canHeal } * HEAL_PER_ENERGY
        val hpB = unitsOfPlayerB.sumBy { it.hitPoints } + unitsOfPlayerB.count { it.canHeal } * HEAL_PER_ENERGY

        val rangeA = unitsOfPlayerA.map { max(it.groundWeapon.type().maxRange(), it.airWeapon.type().maxRange()) }.sum() + 0.1
        val rangeB = unitsOfPlayerB.map { max(it.groundWeapon.type().maxRange(), it.airWeapon.type().maxRange()) }.sum() + 0.1
        val mobilityA = unitsOfPlayerA.map { it.topSpeed }.sum() + 0.1
        val mobilityB = unitsOfPlayerB.map { it.topSpeed }.sum() + 0.1

        val agg = 5000.0 * (damageA / (hpB.toFloat() + .1) - damageB / (hpA.toFloat() + .1)) +
                20 * (rangeA / mobilityB - rangeB / mobilityA)
        return 1.0 / (exp(-agg) + 1)
    }
}

class SimUnit(val name : String = "Unknown",
              val ground: Int = 0,
              val isUnderDarkSwarm: Boolean = false,
              val airUpgrades: Int = 0,
              val groundUpgrades: Int = 0,
              val armorUpgrades: Int = 0,
              var hitPoints: Int = 0,
              var position: Position? = null,
              var tilePosition: TilePosition? = null,
              var canMove: Boolean = false,
              val canHeal: Boolean = false,
              var airWeapon: Weapon = Weapon(WeaponType.None, 0),
              var groundWeapon: Weapon = Weapon(WeaponType.None, 0),
              val isAir: Boolean = false,
              var framesPaused: Int = 0,
              val topSpeed : Int = 0,
              val armor : Int = 0,
              val size : UnitSizeType = UnitSizeType.None) {
    val isVisible = false

    companion object {
        fun of(unit: PlayerUnit): SimUnit = SimUnit(
                name = unit.toString(),
                ground = unit.height(),
                isUnderDarkSwarm = unit is MobileUnit && unit.isUnderDarkSwarm,
                hitPoints = unit.hitPoints,
                position = unit.position,
                tilePosition = unit.tilePosition,
                airWeapon = (unit as? Armed)?.airWeapon ?: Weapon(WeaponType.None, 0),
                groundWeapon = (unit as? Armed)?.groundWeapon ?: Weapon(WeaponType.None, 0),
                canMove = unit is MobileUnit,
                isAir = unit.isFlyer,
                armor = 0,
                size = unit.size)
    }

    /**
     * http://wiki.teamliquid.net/starcraft/Damage
     */
    fun directDamage(other: SimUnit): Double {
        if (other.isUnderDarkSwarm) {
            return 0.0
        }
        val weapon = determineWeaponAgainst(other)
        val weaponType = weapon.type()
        if (weaponType == WeaponType.None) return 0.0
        val weaponUpgrades = if (other.isAir) airUpgrades else groundUpgrades
        val damagePerHit = with(weaponType) { (damageAmount() + damageBonus() * weaponUpgrades) * damageFactor() }
        val damageAfterArmor = max(damagePerHit - other.armor - other.armorUpgrades, 0)
        val damageAfterSize = max(damageAfterArmor.toDouble() * damageTypeModifier(weaponType.damageType(), other.size), 0.5)
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
        return if (weapon == WeaponType.None) 0.0
        else directDamage(other) / weapon.type().damageCooldown()
    }

    fun determineWeaponAgainst(other: SimUnit) = if (other.isAir) airWeapon else groundWeapon

    fun weaponCoolDownVs(other: SimUnit) = if (other.isAir) airWeapon.cooldown() else groundWeapon.cooldown()

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

    override fun toString(): String = "{$name} at ${position} with ${hitPoints} hp"
}
