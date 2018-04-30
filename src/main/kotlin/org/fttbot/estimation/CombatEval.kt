package org.fttbot.estimation

import org.fttbot.fastsig
import org.fttbot.info.isMelee
import org.fttbot.info.isSuicideUnit
import org.fttbot.or
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.*
import org.openbw.bwapi4j.unit.*
import java.lang.Math.pow
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

const val MAX_FRAMES_TO_ATTACK = 3

object CombatEval {
    fun bestProbilityToWin(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>): Pair<List<SimUnit>, Double> {
        var best = unitsOfPlayerA
        var bestEval = probabilityToWin(best, unitsOfPlayerB)
        val typesRemaining = best.map { it.type }.toMutableSet()
        while (!typesRemaining.isEmpty()) {
            val bestType = typesRemaining.map { toTest -> toTest to probabilityToWin(best.filter { it.type != toTest }, unitsOfPlayerB, 192.0) }
                    .maxBy { it.second }!!
            if (bestType.second < bestEval)
                break
            typesRemaining.remove(bestType.first)
            bestEval = bestType.second
            best = best.filter { it.type != bestType.first }
        }
        return best to bestEval
    }

    fun probabilityToWin(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>, fallbackDistance: Double = 192.0): Double {
        val amountOfUnits = max(1, unitsOfPlayerA.size + unitsOfPlayerB.size)
        val center = (unitsOfPlayerA + unitsOfPlayerB)
                .map { it.position }.fold(Position(0, 0)) { s, t -> if (t != null) s.add(t) else s }
                .divide(Position(amountOfUnits, amountOfUnits))

        val medicFactorA = fastsig(unitsOfPlayerA.count { it.canHeal }.toDouble() * 2) * 1.2 + 1.0
        val medicFactorB = fastsig(unitsOfPlayerB.count { it.canHeal }.toDouble() * 2) * 1.2 + 1.0

        val alpha = strength(unitsOfPlayerA, unitsOfPlayerB, center, medicFactorA, fallbackDistance)
        val beta = strength(unitsOfPlayerB, unitsOfPlayerA, center, medicFactorB, fallbackDistance)

        // Lanchester's Law
        val agg = (alpha.average().or(0.0) * pow(unitsOfPlayerA.size.toDouble(), 1.56) -
                beta.average().or(0.0) * pow(unitsOfPlayerB.size.toDouble(), 1.56)) * 0.02
        return 1.0 / (exp(-agg) + 1)
    }

    private fun strength(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>, center: Position, medicFactor: Double, fallbackDistance: Double) =
            unitsOfPlayerA.map {
                val gunRange = max(it.airRange, it.groundRange) + 0.1
                val distance = it.position?.getDistance(center)?.toDouble() ?: fallbackDistance
                val combatRangeFactor = 0.01 + min(0.99, (gunRange + it.topSpeed * 12) / distance)
                val hiddenFactor = if (it.hiddenAttack) 1.3 else 1.0
                val splashFactor = if (it.groundWeapon.type().explosionType() == ExplosionType.Enemy_Splash ||
                        it.groundWeapon.type().explosionType() == ExplosionType.Radial_Splash ||
                        it.airWeapon.type().explosionType() == DamageType.Explosive)
                    (fastsig(unitsOfPlayerB.count { e -> it.determineWeaponAgainst(e).type() != WeaponType.None }.toDouble() * 3.0) * 1.4 + 1.0) else 1.0
                averageDamageOf(it, unitsOfPlayerB) * splashFactor *
                        (if (it.isOrganic) it.hitPoints * medicFactor else it.hitPoints.toDouble() + it.shield) *
                        combatRangeFactor * hiddenFactor *
                        when (it.type) {
                            UnitType.Zerg_Lurker -> 0.68 // Has to walk and burrow before attacking
                            else -> 1.0
                        }

            }

    private fun averageDamageOf(a: SimUnit, unitsB: List<SimUnit>) =
            if (!a.isPowered) 0.0 else
                unitsB.map { b ->
                    if (!b.hiddenAttack || b.detected) {
                        val dmg = a.damagePerFrameTo(b)
                        if (a.suicideUnit) {
                            dmg / unitsB.size / 30
                        } else
                            dmg
                    } else 0.0
                }.average().or(2.0)
}

class SimUnit(val id: Int? = 0,
              val name: String = "Unknown",
              val ground: Int = 0,
              val isUnderDarkSwarm: Boolean = false,
              val airUpgrades: Int = 0,
              val groundUpgrades: Int = 0,
              val armorUpgrades: Int = 0,
              var hitPoints: Int = 0,
              var shield: Int = 0,
              var position: Position? = null,
              val canHeal: Boolean = false,
              var airWeapon: Weapon = Weapon(WeaponType.None, 0),
              var groundWeapon: Weapon = Weapon(WeaponType.None, 0),
              val isAir: Boolean = false,
              val topSpeed: Double = 0.0,
              val armor: Int = 0,
              val size: UnitSizeType = UnitSizeType.None,
              val hiddenAttack: Boolean = false,
              val detected: Boolean = false,
              val isPowered: Boolean = true,
              val type: UnitType,
              val airHits: Int,
              val groundHits: Int,
              val groundRange: Int,
              val airRange: Int,
              val suicideUnit: Boolean,
              val isOrganic: Boolean,
              val isHidden: Boolean) {
    val isAttacker = groundWeapon.type() != WeaponType.None || airWeapon.type() != WeaponType.None

    fun canAttack(other: SimUnit, safety: Int = 0): Boolean {
        val distance = position?.getDistance(other.position!!) ?: 0
        return other.detected &&
                ((other.isAir && airWeapon != WeaponType.None && airRange + safety >= distance && distance >= airWeapon.type().minRange()) ||
                        (!other.isAir && groundWeapon != WeaponType.None && groundRange + safety >= distance && distance >= groundWeapon.type().minRange()))
    }

    companion object {
        fun of(unit: PlayerUnit): SimUnit = SimUnit(
                id = unit.id,
                name = unit.toString(),
                ground = unit.height(),
                isUnderDarkSwarm = unit is MobileUnit && unit.isUnderDarkSwarm,
                hitPoints = unit.hitPoints,
                shield = unit.shields,
                position = unit.position,
                canHeal = unit.initialType == UnitType.Terran_Medic,
                airWeapon = (unit as? AirAttacker)?.airWeapon
                        ?: if (unit is Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(WeaponType.None, 0),
                groundWeapon = (unit as? GroundAttacker)?.groundWeapon
                        ?: if (unit is Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(WeaponType.None, 0),
                isAir = unit.isFlying,
                topSpeed = (unit as? MobileUnit)?.topSpeed ?: 0.0,
                armor = 0,
                size = unit.size,
                hiddenAttack = unit.isCloaked || unit is Lurker,
                detected = unit.isDetected,
                isPowered = unit.isPowered,
                type = unit.initialType,
                airHits = if (unit is Bunker) 1 else unit.initialType.maxAirHits(),
                groundHits = if (unit is Bunker) 1 else unit.initialType.maxGroundHits(),
                groundRange = ((unit as? GroundAttacker)?.groundWeaponMaxRange ?: 0) + (if (unit is Bunker) 64 else 0),
                airRange = ((unit as? AirAttacker)?.airWeaponMaxRange ?: 0) + if (unit is Bunker) 64 else 0,
                suicideUnit = unit.isSuicideUnit,
                isOrganic = unit.initialType.isOrganic,
                isHidden = unit is Lurker && (unit.isBurrowed || unit.order == Order.Burrowing || unit.order == Order.Cloak) || unit is Cloakable && unit.isCloaked)

        fun of(type: UnitType): SimUnit = SimUnit(
                name = type.name,
                hitPoints = type.maxHitPoints(),
                shield = type.maxShields(),
                canHeal = type == UnitType.Terran_Medic,
                airWeapon = if (type == UnitType.Terran_Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(type.airWeapon(), 0),
                groundWeapon = if (type == UnitType.Terran_Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(type.groundWeapon(), 0),
                isAir = type.isFlyer,
                topSpeed = type.topSpeed(),
                size = type.size(),
                hiddenAttack = type == UnitType.Zerg_Lurker,
                detected = true,
                type = type,
                airHits = if (type == UnitType.Terran_Bunker) 1 else type.maxAirHits(),
                groundHits = if (type == UnitType.Terran_Bunker) 1 else type.maxGroundHits(),
                groundRange = type.groundWeapon().maxRange() + if (type == UnitType.Terran_Bunker) 2 else 0,
                airRange = type.airWeapon().maxRange() + if (type == UnitType.Terran_Bunker) 2 else 0,
                suicideUnit = when (type) { UnitType.Zerg_Scourge, UnitType.Terran_Vulture_Spider_Mine, UnitType.Protoss_Scarab -> true; else -> false
                },
                isOrganic = type.isOrganic,
                isHidden = false
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
        else directDamage(other) / weapon.type().damageCooldown() *
                (if (type == UnitType.Terran_Bunker) 50.0 else 1.0)
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
