package org.fttbot.estimation

import com.badlogic.gdx.math.Vector2
import org.fttbot.*
import org.fttbot.info.isMelee
import org.fttbot.info.isSuicideUnit
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.*
import org.openbw.bwapi4j.unit.*
import java.lang.Math.pow
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

const val MAX_FRAMES_TO_ATTACK = 24

object CombatEval {
    var strengthPower = 0.7
    var amountPower = 1.0
    var evalScale = 0.09

    fun minAmountOfAdditionalsForProbability(myUnits: List<SimUnit>, additionalUnits: SimUnit, enemies: List<SimUnit>, minProbability: Double = 0.6): Int {
        if (enemies.isEmpty()) return 0

        var a = 0
        var b = 50

        while (b > a) {
            val m = (a + b) / 2
            val unitsA = (1..m).map { additionalUnits }
            val result = probabilityToWin(myUnits + unitsA, enemies)
            if (result >= minProbability)
                b = m
            else
                a = m + 1
        }

        return if (b < 50) b else -1
    }

    fun bestEnemyToKill(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>, fightingPosition: Float = 0.5f): SimUnit? =
            unitsOfPlayerB.maxBy {
                probabilityToWin(unitsOfPlayerA, unitsOfPlayerB - it, fightingPosition)
            }

    fun bestProbilityToWin(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>, minProbabilityToAchieve: Double = 0.8, fightingPosition: Float = 0.5f): Pair<List<SimUnit>, Double> {
        var best = unitsOfPlayerA
        var bestEval = probabilityToWin(best, unitsOfPlayerB)
        val typesRemaining = best.map { it.type }.toMutableSet()
        while (!typesRemaining.isEmpty() && bestEval < minProbabilityToAchieve) {
            val bestType = typesRemaining.map { toTest -> toTest to probabilityToWin(best.filter { it.type != toTest }, unitsOfPlayerB, fightingPosition) }
                    .maxBy { it.second }!!
            if (bestType.second < bestEval)
                break
            typesRemaining.remove(bestType.first)
            bestEval = bestType.second
            best = best.filter { it.type != bestType.first }
        }
        return best to bestEval
    }

    fun probabilityToWin(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>, fightingPosition: Float = 0.5f, fallbackDistance: Double = 128.0): Double {
        val positionedUnits = (unitsOfPlayerA.mapNotNull { it.position?.toVector()?.scl(1 - fightingPosition) } +
                unitsOfPlayerB.mapNotNull { it.position?.toVector()?.scl(fightingPosition) })
        val center = positionedUnits
                .fold(Vector2(0f, 0f), Vector2::add)
                .scl(1f / positionedUnits.size)
                .toPosition()

        val medicFactorA = fastsig(unitsOfPlayerA.count { it.canHeal }.toDouble() * 2) * 1.5 + 1.0
        val medicFactorB = fastsig(unitsOfPlayerB.count { it.canHeal }.toDouble() * 2) * 1.5 + 1.0
        val repairFactorA = fastsig(unitsOfPlayerA.count { it.canRepair }.toDouble() * 0.15) * 2.7 + 1.0
        val repairFactorB = fastsig(unitsOfPlayerB.count { it.canRepair }.toDouble() * 0.15) * 2.7 + 1.0

        val alpha = strength(unitsOfPlayerA, unitsOfPlayerB.filter { !it.suicideUnit }, center, medicFactorA, repairFactorA, fallbackDistance)
        val beta = strength(unitsOfPlayerB, unitsOfPlayerA.filter { !it.suicideUnit }, center, medicFactorB, repairFactorB, fallbackDistance)

        // Lanchester's Law
        val eA = alpha.map { pow(it, strengthPower) }.average().or(0.0)
        val eB = beta.map { pow(it, strengthPower) }.average().or(0.0)
        val agg = (eA * pow(unitsOfPlayerA.size.toDouble(), amountPower) -
                eB * pow(unitsOfPlayerB.size.toDouble(), amountPower)) * evalScale
        val result = 1.0 / (exp(-agg) + 1)
        if (result.isNaN())
            throw IllegalStateException()
        return result
    }

    fun attackScore(attackerSim: SimUnit, enemySim: SimUnit): Double {
        val wpn = attackerSim.determineWeaponAgainst(enemySim)
        if (wpn.type() == WeaponType.None || !enemySim.detected) return -100000.0;
        val enemyWpn = enemySim.determineWeaponAgainst(attackerSim)
        val futurePosition = enemySim.position?.add(enemySim.velocity.scl(24f * 3).toPosition())
        val futureDistance = futurePosition?.minus(attackerSim.position
                ?: futurePosition)?.toVector()?.len() ?: 30f
        return (enemySim.hitPoints + enemySim.shield) / max(attackerSim.damagePerFrameTo(enemySim), 0.001) +
                0.5 * (attackerSim.hitPoints + attackerSim.shield) / max(enemySim.damagePerFrameTo(attackerSim), 0.001) +
                (if (enemySim.type == UnitType.Zerg_Larva || enemySim.type == UnitType.Zerg_Egg || enemySim.type == UnitType.Protoss_Interceptor || enemySim.type.isAddon) 25000 else 0) +
                (if (enemySim.type.isWorker || enemySim.canHeal || enemySim.canRepair) -150 else 0) +
                (if (enemySim.type.spaceProvided() > 0) -200 else 0) +
                (if (attackerSim.isAir) 0.0 else 1.0 * futureDistance) +
                1.5 * (enemySim.position?.getDistance(attackerSim.position) ?: 0) +
                (if (attackerSim.hasSplashWeapon) -100 else 0) +
                (fastsig(max(0.0, futureDistance.toDouble() - enemyWpn.maxRange())) * -100 *
                        (if (enemySim.type == UnitType.Zerg_Lurker && enemySim.isBurrowed) 20.0 else 1.0)) +
                (if (enemySim.type == UnitType.Protoss_Carrier) -300 else 0) +
                (fastsig(max(0.0, futureDistance.toDouble() - wpn.maxRange())) * 150 *
                        (if (attackerSim.type == UnitType.Zerg_Lurker && attackerSim.isBurrowed) 20.0 else 1.0)) +
                (enemySim.topSpeed - attackerSim.topSpeed) * 30
    }

    private fun strength(unitsOfPlayerA: List<SimUnit>, unitsOfPlayerB: List<SimUnit>, center: Position, medicFactor: Double, repairFactor: Double, fallbackDistance: Double) =
            unitsOfPlayerA.map {
                val gunRange = max(it.airRange, it.groundRange) + 0.1
                val distance = it.position?.getDistance(center)?.toDouble() ?: fallbackDistance
                val combatRangeFactor = 0.01 + min(0.99, (gunRange + it.topSpeed * 5) / distance)
                val splashFactor = if (it.hasSplashWeapon)
                    (max(0.0, fastsig(unitsOfPlayerB.count { e -> it.determineWeaponAgainst(e).type() != WeaponType.None }.toDouble() - 1) - 0.5) *
                            when (it.type) {
                                UnitType.Zerg_Mutalisk -> 0.35
                                else -> 3.0
                            } + 1.0)
                else 1.0
                averageDamageOf(it, unitsOfPlayerB) * splashFactor *
                        (if (it.isOrganic) (it.hitPoints + it.shield) * medicFactor
                        else if (it.isRepairable) (it.hitPoints.toDouble() + it.shield) * repairFactor
                        else it.hitPoints.toDouble() + it.shield) *
                        combatRangeFactor *
                        when (it.type) {
                            UnitType.Zerg_Lurker -> if (it.isBurrowed) 0.9 else 0.7 // Has to walk and burrow before attacking
                            UnitType.Terran_Marine -> 1.3 // Pretend stim
                            else -> 1.0
                        }

            }

    private fun averageDamageOf(a: SimUnit, unitsB: List<SimUnit>) =
            if (!a.isPowered) 0.0
            else if (unitsB.isEmpty()) 0.4
            else {
                val damages = unitsB.map { b -> if (b.detected) a.damagePerFrameTo(b) else 0.0 }.filter { it > 0.0 }
                if (damages.isEmpty()) 0.0
                else if (a.suicideUnit) damages.average() / damages.size / 2.0
                else damages.average()
            }
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
              var velocity: Vector2 = Vector2(),
              val canHeal: Boolean = false,
              val canRepair: Boolean = false,
              var airWeapon: Weapon = Weapon(WeaponType.None, 0),
              var groundWeapon: Weapon = Weapon(WeaponType.None, 0),
              val isAir: Boolean = false,
              val topSpeed: Double = 0.0,
              val armor: Int = 0,
              val size: UnitSizeType = UnitSizeType.None,
              var detected: Boolean = false,
              val isPowered: Boolean = true,
              val type: UnitType,
              val airHits: Int,
              val groundHits: Int,
              var groundRange: Int,
              var airRange: Int,
              val suicideUnit: Boolean,
              val isOrganic: Boolean,
              val isRepairable: Boolean,
              var isCloaked: Boolean,
              var isBurrowed: Boolean) {
    val isAttacker = groundWeapon.type() != WeaponType.None || airWeapon.type() != WeaponType.None
    val left get() = (position?.x ?: 0) - this.type.dimensionLeft()
    val top get() = (position?.y ?: 0) - this.type.dimensionUp()
    val right get() = (position?.x ?: 0) + this.type.dimensionRight()
    val bottom get() = (position?.y ?: 0) + this.type.dimensionDown()
    val hasSplashWeapon = groundWeapon.type().explosionType() == ExplosionType.Enemy_Splash ||
            groundWeapon.type().explosionType() == ExplosionType.Radial_Splash ||
            airWeapon.type().explosionType() == DamageType.Explosive ||
            airWeapon.type().explosionType() == ExplosionType.Air_Splash ||
            groundWeapon.type() == WeaponType.Glave_Wurm


    fun canAttack(other: SimUnit, safety: Int = 0): Boolean {
        val distance = getDistance(other)
        return other.detected &&
                ((other.isAir && airWeapon != WeaponType.None && airRange + safety >= distance && distance >= airWeapon.type().minRange()) ||
                        (!other.isAir && groundWeapon != WeaponType.None && groundRange + safety >= distance && distance >= groundWeapon.type().minRange()))
    }

    fun getDistance(target: SimUnit): Int {
        if (this === target) {
            return 0
        }

        var xDist = left - (target.right + 1)
        if (xDist < 0) {
            xDist = target.left - (right + 1)
            if (xDist < 0) {
                xDist = 0
            }
        }
        var yDist = top - (target.bottom + 1)
        if (yDist < 0) {
            yDist = target.top - (bottom + 1)
            if (yDist < 0) {
                yDist = 0
            }
        }

        return Position(0, 0).getDistance(Position(xDist, yDist))
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
                velocity = Vector2(unit.velocityX.toFloat(), unit.velocityY.toFloat()),
                canHeal = unit is Medic,
                canRepair = unit is SCV,
                airWeapon = (unit as? AirAttacker)?.airWeapon
                        ?: if (unit is Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(WeaponType.None, 0),
                groundWeapon = (unit as? GroundAttacker)?.groundWeapon
                        ?: if (unit is Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(WeaponType.None, 0),
                isAir = unit.isFlying,
                topSpeed = (unit as? MobileUnit)?.topSpeed ?: 0.0,
                armor = 0,
                size = unit.size,
                detected = unit.isDetected,
                isPowered = unit.isPowered,
                type = unit.type,
                airHits = if (unit is Bunker) 1 else unit.type.maxAirHits(),
                groundHits = if (unit is Bunker) 1 else unit.type.maxGroundHits(),
                groundRange = ((unit as? GroundAttacker)?.groundWeaponMaxRange
                        ?: 0) + (if (unit is Bunker) 64 else 0),
                airRange = ((unit as? AirAttacker)?.airWeaponMaxRange
                        ?: 0) + if (unit is Bunker) 64 else 0,
                suicideUnit = unit.isSuicideUnit,
                isOrganic = unit.type.isOrganic,
                isRepairable = unit is Mechanical && unit.type.race == Race.Terran,
                isCloaked = unit is Cloakable && (unit.isCloaked || unit.order == Order.Cloak),
                isBurrowed = unit is Burrowable && (unit.isBurrowed || unit.order == Order.Burrowing)
        )

        fun of(type: UnitType): SimUnit = SimUnit(
                name = type.name,
                hitPoints = type.maxHitPoints(),
                shield = type.maxShields(),
                canHeal = type == UnitType.Terran_Medic,
                canRepair = type == UnitType.Terran_SCV,
                airWeapon = if (type == UnitType.Terran_Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(type.airWeapon(), 0),
                groundWeapon = if (type == UnitType.Terran_Bunker) Weapon(WeaponType.Gauss_Rifle, 0) else Weapon(type.groundWeapon(), 0),
                isAir = type.isFlyer,
                topSpeed = type.topSpeed(),
                size = type.size(),
                detected = true,
                type = type,
                airHits = if (type == UnitType.Terran_Bunker) 1 else type.maxAirHits(),
                groundHits = if (type == UnitType.Terran_Bunker) 1 else type.maxGroundHits(),
                groundRange = type.groundWeapon().maxRange() + if (type == UnitType.Terran_Bunker) 2 else 0,
                airRange = type.airWeapon().maxRange() + if (type == UnitType.Terran_Bunker) 2 else 0,
                suicideUnit = when (type) { UnitType.Zerg_Scourge, UnitType.Terran_Vulture_Spider_Mine, UnitType.Protoss_Scarab -> true; else -> false
                },
                isOrganic = type.isOrganic,
                isRepairable = type.isMechanical && type.race == Race.Terran,
                isBurrowed = false,
                isCloaked = false
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
                (if (type == UnitType.Terran_Bunker) 100.0 else 1.0)
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
