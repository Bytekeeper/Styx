package org.fttbot.estimation

import org.fttbot.layer.isMelee
import org.fttbot.or
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.DamageType
import org.openbw.bwapi4j.type.UnitSizeType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import kotlin.math.exp
import kotlin.math.max

const val MAX_FRAMES_TO_ATTACK = 25
const val HEAL_PER_ENERGY = 2

object CombatEval {
    fun probabilityToWin(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>): Double {
        val damageA = unitsOfPlayerA.map { a ->
            unitsOfPlayerB.map { b -> a.damagePerFrameTo(b) }.max() ?: 0.01
        }.average().or(0.01)
        val damageB = unitsOfPlayerB.map { b ->
            unitsOfPlayerA.map { a -> b.damagePerFrameTo(a) }.max() ?: 0.01
        }.average().or(0.01)
        val hpA = unitsOfPlayerA.filter { it.isArmed }.map { it.hitPoints }.average().or(0.1) + unitsOfPlayerA.count { it.canHeal } * HEAL_PER_ENERGY
        val hpB = unitsOfPlayerB.filter { it.isArmed }.map { it.hitPoints }.average().or(0.1) + unitsOfPlayerB.count { it.canHeal } * HEAL_PER_ENERGY

        val rangeA = unitsOfPlayerA.map { max(it.groundWeapon.type().maxRange(), it.airWeapon.type().maxRange()) }.average().or(0.1)
        val rangeB = unitsOfPlayerB.map { max(it.groundWeapon.type().maxRange(), it.airWeapon.type().maxRange()) }.average().or(0.1)
        val mobilityA = unitsOfPlayerA.map { it.topSpeed }.average().or(0.1)
        val mobilityB = unitsOfPlayerB.map { it.topSpeed }.average().or(0.1)

        // Lanchester's Law
        val agg = 0.5 * (rangeA / mobilityB * damageA / hpB * unitsOfPlayerA.size * unitsOfPlayerA.size -
                rangeB / mobilityA * damageB / hpA * unitsOfPlayerB.size * unitsOfPlayerB.size)
        return 1.0 / (exp(-agg) + 1)
    }
}

class SimUnit(val name: String = "Unknown",
              val ground: Int = 0,
              val isUnderDarkSwarm: Boolean = false,
              val airUpgrades: Int = 0,
              val groundUpgrades: Int = 0,
              val armorUpgrades: Int = 0,
              var hitPoints: Int = 0,
              var position: Position? = null,
              val canHeal: Boolean = false,
              var airWeapon: Weapon = Weapon(WeaponType.None, 0),
              var groundWeapon: Weapon = Weapon(WeaponType.None, 0),
              val isAir: Boolean = false,
              val topSpeed: Double = 0.0,
              val armor: Int = 0,
              val size: UnitSizeType = UnitSizeType.None) {
    val isArmed = groundWeapon.type() != WeaponType.None || airWeapon.type() != WeaponType.None

    companion object {
        fun of(unit: PlayerUnit): SimUnit = SimUnit(
                name = unit.toString(),
                ground = unit.height(),
                isUnderDarkSwarm = unit is MobileUnit && unit.isUnderDarkSwarm,
                hitPoints = unit.hitPoints,
                position = unit.position,
                airWeapon = (unit as? Armed)?.airWeapon ?: if (unit is Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(WeaponType.None, 0),
                groundWeapon = (unit as? Armed)?.groundWeapon ?: if (unit is Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(WeaponType.None, 0),
                isAir = unit.isFlyer,
                armor = 0,
                size = unit.size,
                topSpeed = unit.topSpeed())
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
        return if (weapon.type() == WeaponType.None) 0.0
        else directDamage(other) / weapon.type().damageCooldown()
    }

    fun determineWeaponAgainst(other: SimUnit) = if (other.isAir) airWeapon else groundWeapon

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
