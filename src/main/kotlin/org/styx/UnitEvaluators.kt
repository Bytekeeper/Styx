package org.styx

import bwapi.Position
import bwapi.UnitType
import bwapi.WeaponType
import org.bk.ass.sim.Agent
import org.bk.ass.sim.Simulator
import org.locationtech.jts.math.DD.EPS
import org.styx.Config.evalFramesToKillFactor
import org.styx.Config.evalFramesToReach
import org.styx.Config.evalFramesToTurnFactor
import org.styx.Config.waitForReinforcementsTargetingFactor
import java.util.function.ToIntFunction
import kotlin.math.max

typealias TargetScorer = (a: SUnit, e: SUnit) -> Double

interface CombatMove
data class AttackMove(val enemy: SUnit) : CombatMove
class WaitMove() : CombatMove

object TargetEvaluator {
    private val byEnemyType: TargetScorer = { _, e ->
        when (e.unitType) {
            UnitType.Zerg_Egg, UnitType.Zerg_Lurker_Egg, UnitType.Zerg_Larva -> -10.0
            UnitType.Zerg_Extractor, UnitType.Terran_Refinery, UnitType.Protoss_Assimilator -> -2.0
            UnitType.Zerg_Drone, UnitType.Protoss_Probe, UnitType.Terran_SCV -> 0.8
            UnitType.Terran_Medic, UnitType.Protoss_High_Templar -> 0.7
            else -> 0.0
        }
    }

    private val byTimeToAttack: TargetScorer = { a, e ->
        val framesToTurnTo = a.framesToTurnTo(e)
        -max(0, a.distanceTo(e) - a.maxRangeVs(e)) * evalFramesToReach / max(1.0, a.topSpeed) -
                framesToTurnTo * evalFramesToTurnFactor
    }

    private val byTimeToKillEnemy: TargetScorer = { a, e ->
        val damageAmount = a.weaponAgainst(e).damageAmount()
        -(e.hitPoints / (a.damagePerFrameVs(e) + EPS) - e.shields / (damageAmount + EPS)) * evalFramesToKillFactor
    }

    private val byEnemyRange: TargetScorer = { a, e ->
        e.maxRangeVs(a) / 600.0
    }

    private val byEnemyDamageCapabilities: TargetScorer = { a, e ->
        if (e.hasPower) e.damagePerFrameVs(a) * 1.6 else 0.0
    }

    private val defaultScorers = listOf(byEnemyType, byTimeToAttack, byTimeToKillEnemy, byEnemyRange, byEnemyDamageCapabilities)

    fun bestCombatMoves(attackers: Collection<SUnit>, targets: Collection<SUnit>): Map<SUnit, CombatMove?> {
        val relevantTargets = targets
                .filter { it.detected && it.unitType != UnitType.Zerg_Larva}
        if (relevantTargets.isEmpty() || attackers.isEmpty())
            return mapOf()
        val alreadyEngaged = relevantTargets.any { e -> attackers.any { e.inAttackRange(it, 48) || it.inAttackRange(e, 48) } } || relevantTargets.none { it.unitType.canAttack() }
        val averageMinimumDistanceToEnemy = if (alreadyEngaged) 0.0 else attackers.map { a -> relevantTargets.map { a.distanceTo(it) }.min()!! }.average()
        val pylons = 4.0 * (1 - fastSig(targets.count { it.unitType == UnitType.Protoss_Pylon }.toDouble()))
        val defensiveBuildings = targets.count {
            it.remainingBuildTime < 48 &&
                    (it.unitType == UnitType.Protoss_Photon_Cannon || it.unitType == UnitType.Protoss_Shield_Battery)
        }

        val pylonScorer: TargetScorer = { _, e -> if (defensiveBuildings > 1 && e.unitType == UnitType.Protoss_Pylon) pylons else 0.0 }
        val scorers = defaultScorers.asSequence() + pylonScorer

        val config = attackers.mapNotNull { a ->
            val target = relevantTargets.asSequence()
                    .filter { a.hasWeaponAgainst(it) }
                    .maxBy { e -> scorers.sumByDouble { it(a, e) } }
            target?.let { a to target }
        }
        return config.map { (a, e) ->
            a to
                    if (a.distanceTo(e) < averageMinimumDistanceToEnemy * waitForReinforcementsTargetingFactor && e.unitType.canAttack())
                        WaitMove()
                    else
                        AttackMove(e)
        }.toMap()
    }

    fun bestTarget(attacker: SUnit, targets: Collection<SUnit>): SUnit? {
        val best= targets
                .filter {
                    it.detected &&
                            attacker.weaponAgainst(it) != WeaponType.None &&
                            it.unitType != UnitType.Zerg_Larva
                }.map { enemy ->
                    val sumByDouble = defaultScorers.sumByDouble { it(attacker, enemy) }
                    require(!sumByDouble.isNaN())
                    enemy to sumByDouble
                }
                .maxBy { it.second }?.first
        return best
    }
}

typealias UnitEvaluator = (SUnit) -> Double

fun closeTo(position: Position): UnitEvaluator = { u ->
    u.framesToTravelTo(position) +
            (if (u.carrying) 100.0 else 0.0)
}

fun unitThreatValueToEnemy(sUnit: SUnit): Int {
    val unitType = sUnit.unitType
    if (unitType == UnitType.Terran_Bunker) {
        val bunkerWeapon = WeaponType.Gauss_Rifle
        val bunkerDPF = ((bunkerWeapon.damageAmount() * 4) / bunkerWeapon.damageCooldown().toDouble()).orZero()
        return (bunkerDPF * 41).toInt()
    }
    val airWeapon = unitType.airWeapon()
    val airDPF = ((airWeapon.damageAmount() * unitType.maxAirHits()) / airWeapon.damageCooldown().toDouble()).orZero() *
            (if (airWeapon.isSplash) 3 else 1)
    val groundWeapon = unitType.groundWeapon()
    val groundDPF = ((groundWeapon.damageAmount() * unitType.maxGroundHits()) / groundWeapon.damageCooldown().toDouble()).orZero() *
            (if (groundWeapon.isSplash) 3 else 1)
    return (max(airDPF, groundDPF) * 41).toInt()
}

fun agentHealthAndShieldValue(agent: Agent): Int =
        Simulator.HEALTH_AND_HALFED_SHIELD.applyAsInt(agent) / 15

fun unitTypeValue(sUnit: SUnit) = when (sUnit.unitType) {
    UnitType.Zerg_Drone, UnitType.Terran_SCV, UnitType.Protoss_Probe -> 5
    UnitType.Terran_Medic, UnitType.Protoss_High_Templar, UnitType.Terran_Science_Vessel -> 3
    else -> 2
}

// Simplified, but should take more stuff into account:
// Basic idea: We might want to sacrifice units in order to gain global value. Ie. lose lings but kill workers in early game
val agentValueForPlayer = ToIntFunction<Agent> { agent ->
    val sUnit = agent.userObject as SUnit? ?: return@ToIntFunction 0
    unitThreatValueToEnemy(sUnit) +
            agentHealthAndShieldValue(agent) +
            unitTypeValue(sUnit)
}