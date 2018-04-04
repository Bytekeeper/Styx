package org.fttbot.estimation

import org.fttbot.info.isMelee
import org.fttbot.or
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.DamageType
import org.openbw.bwapi4j.type.UnitSizeType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import kotlin.math.exp
import kotlin.math.max

const val MAX_FRAMES_TO_ATTACK = 25
const val HEAL_SCORE = 50

object CombatEval {
    fun probabilityToWin(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>): Double {
        val damageA = averageDamageOf(unitsOfPlayerA, unitsOfPlayerB)
        val damageB = averageDamageOf(unitsOfPlayerB, unitsOfPlayerA)
        val hpA = unitsOfPlayerA.filter { it.isAttacker }.map { it.hitPoints }.average().or(0.1) +
                unitsOfPlayerA.count { it.canHeal } * HEAL_SCORE
        val hpB = unitsOfPlayerB.filter { it.isAttacker }.map { it.hitPoints }.average().or(0.1) +
                unitsOfPlayerB.count { it.canHeal } * HEAL_SCORE

        val rangeA = (unitsOfPlayerA.map { max(it.groundWeapon.type().maxRange() + it.groundBonusRange, it.airWeapon.type().maxRange() + it.airBonusRange) } + 1).average()
        val rangeB = (unitsOfPlayerB.map { max(it.groundWeapon.type().maxRange() + it.groundBonusRange, it.airWeapon.type().maxRange() + it.airBonusRange) } + 1).average()
        val mobilityA = (unitsOfPlayerA.map { it.topSpeed } + 0.01).average()
        val mobilityB = (unitsOfPlayerB.map { it.topSpeed } + 0.01).average()

        val amountOfUnits = max(1, unitsOfPlayerA.size + unitsOfPlayerB.size)
        val center = (unitsOfPlayerA + unitsOfPlayerB)
                .map { it.position }.fold(Position(0, 0)) { s, t -> if (t != null) s.add(t) else s }
                .divide(Position(amountOfUnits, amountOfUnits))
        val distA = unitsOfPlayerA.map {
            max(0, it.position?.getDistance(center) ?: 0-max(it.airWeapon.maxRange(), it.groundWeapon.maxRange()))
        }
                .average().or(1000.0)
        val distB = unitsOfPlayerB.map {
            max(0, it.position?.getDistance(center) ?: 0-max(it.airWeapon.maxRange(), it.groundWeapon.maxRange()))
        }
                .average().or(1000.0)

        // Lanchester's Law
//        val agg = 0.1 * ((distB / 100.0 + mobilityA / 100.0 + rangeA / 1000.0 + 4000.0 *  damageA / hpB) * unitsOfPlayerA.size * unitsOfPlayerA.size -
//                (distA / 100.0 + mobilityB / 100.0 + rangeB / 1000.0 + 4000.0 * damageB / hpA) * unitsOfPlayerB.size * unitsOfPlayerB.size)
        val agg = 0.1 * ((mobilityA / 80.0 + rangeA / 800.0 + damageA / 0.2 + hpA / 800.0 + distB / 8000.0) * unitsOfPlayerA.size * unitsOfPlayerA.size -
                (mobilityB / 80.0 + rangeB / 800.0 + damageB / 0.2 + hpB / 800.0 + distA / 8000.0) * unitsOfPlayerB.size * unitsOfPlayerB.size)
        return 1.0 / (exp(-agg) + 1)
    }

    private fun averageDamageOf(unitsA: List<SimUnit>, unitsB: List<SimUnit>) = (unitsA.map { a ->
        if (!a.isPowered) 0.0 else
            unitsB.map { b -> if (!b.hidden || b.detected) a.damagePerFrameTo(b) else 0.0 }.average()
    }).average().or(0.1)
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
              val size: UnitSizeType = UnitSizeType.None,
              val hidden: Boolean = false,
              val detected: Boolean = false,
              val isPowered: Boolean = true,
              val type: UnitType,
              val airHits: Int,
              val groundHits: Int,
              val groundBonusRange: Int,
              val airBonusRange: Int) {
    val isAttacker = groundWeapon.type() != WeaponType.None || airWeapon.type() != WeaponType.None

    companion object {
        fun of(unit: PlayerUnit): SimUnit = SimUnit(
                name = unit.toString(),
                ground = unit.height(),
                isUnderDarkSwarm = unit is MobileUnit && unit.isUnderDarkSwarm,
                hitPoints = unit.hitPoints,
                position = unit.position,
                canHeal = unit.initialType == UnitType.Terran_Medic,
                airWeapon = (unit as? AirAttacker)?.airWeapon
                        ?: if (unit is Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(WeaponType.None, 0),
                groundWeapon = (unit as? GroundAttacker)?.groundWeapon
                        ?: if (unit is Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(WeaponType.None, 0),
                isAir = unit.isFlyer,
                topSpeed = (unit as? MobileUnit)?.topSpeed ?: 0.0,
                armor = 0,
                size = unit.size,
                hidden = unit.isCloaked || unit is Burrowable && unit.isBurrowed,
                detected = unit.isDetected,
                isPowered = unit.isPowered,
                type = unit.initialType,
                airHits = unit.initialType.maxAirHits(),
                groundHits = unit.initialType.maxGroundHits(),
                groundBonusRange = if (unit is Bunker) 64 else 0,
                airBonusRange = if (unit is Bunker) 64 else 0)

        fun of(type: UnitType): SimUnit = SimUnit(
                name = type.name,
                hitPoints = type.maxHitPoints(),
                canHeal = type == UnitType.Terran_Medic,
                airWeapon = if (type == UnitType.Terran_Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(type.airWeapon(), 0),
                groundWeapon = if (type == UnitType.Terran_Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(type.airWeapon(), 0),
                isAir = type.isFlyer,
                topSpeed = type.topSpeed(),
                size = type.size(),
                type = type,
                airHits = type.maxAirHits(),
                groundHits = type.maxGroundHits(),
                groundBonusRange = if (type == UnitType.Terran_Bunker) 2 else 0,
                airBonusRange = if (type == UnitType.Terran_Bunker) 2 else 0
        )
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
        val damagePerHit = with(weaponType) { (damageAmount() + damageBonus() * weaponUpgrades) * damageFactor() } *
                (if (other.isAir) airHits else groundHits)
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
                    UnitSizeType.Independent -> 0.0
                    else -> throw IllegalStateException("Can't compare against ${unitSizeType}")
                }
                DamageType.Explosive -> when (unitSizeType) {
                    UnitSizeType.Small -> 0.5
                    UnitSizeType.Medium -> 0.75
                    UnitSizeType.Large -> 1.0
                    UnitSizeType.Independent -> 0.0
                    else -> throw IllegalStateException("Can't compare against ${unitSizeType}")
                }
                DamageType.Normal -> 1.0
                DamageType.Ignore_Armor -> 1.0
                else -> throw IllegalStateException("Can't compare with ${damageType}")
            }

    override fun toString(): String = "{$name} at ${position} with ${hitPoints} hp"
}
