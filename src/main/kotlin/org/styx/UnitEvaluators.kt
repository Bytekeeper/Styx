package org.styx

import bwapi.Position
import bwapi.UnitType
import bwapi.WeaponType
import org.bk.ass.sim.Agent
import org.bk.ass.sim.Simulator
import org.locationtech.jts.math.DD.EPS
import org.styx.Config.dpfThreatValueFactor
import org.styx.Config.evalFramesToKillFactor
import org.styx.Config.evalFramesToReach
import org.styx.Config.evalFramesToTurnFactor
import org.styx.Config.waitForReinforcementsTargetingFactor
import java.util.function.ToIntFunction
import kotlin.math.max

typealias TargetScorer = (a: SUnit, e: SUnit, aggressiveNess: Double) -> Double

interface CombatMove {
    val unit: SUnit
}

data class AttackMove(override val unit: SUnit, val enemy: SUnit) : CombatMove
data class WaitMove(override val unit: SUnit) : CombatMove
data class Disengage(override val unit: SUnit) : CombatMove

object TargetEvaluator {
    private val byEnemyType: TargetScorer = { _, e, _ ->
        when (e.unitType) {
            UnitType.Zerg_Egg, UnitType.Zerg_Lurker_Egg, UnitType.Zerg_Larva -> -10.0
            UnitType.Zerg_Extractor, UnitType.Terran_Refinery, UnitType.Protoss_Assimilator, UnitType.Protoss_Interceptor -> -2.0
            UnitType.Zerg_Drone -> 0.9
            UnitType.Protoss_Probe, UnitType.Terran_SCV -> 0.4
            UnitType.Terran_Medic, UnitType.Protoss_High_Templar -> 0.1
            else -> 0.0
        }
    }

    private val byTimeToAttack: TargetScorer = { a, e, aggressiveness ->
        val framesToTurnTo = a.framesToTurnTo(e)
        -(max(0, a.distanceTo(e) - a.maxRangeVs(e)) * evalFramesToReach / max(1.0, a.topSpeed) +
                framesToTurnTo * evalFramesToTurnFactor) / aggressiveness
    }

    private val byTimeToKillEnemy: TargetScorer = { a, e, _ ->
        val damageAmount = a.weaponAgainst(e).damageAmount()
        -(e.hitPoints / (a.damagePerFrameVs(e) + EPS) - e.shields / (damageAmount + EPS)) * evalFramesToKillFactor
    }

    private val byEnemyRange: TargetScorer = { a, e, _ ->
        if (e.hasPower) e.maxRangeVs(a) / 1000.0 else 0.0
    }

    private val byEnemySpeed: TargetScorer = { a, e, _ ->
        -e.topSpeed / 40
    }

    private val byEnemyDamageCapabilities: TargetScorer = { a, e, _ ->
        -e.cooldownRemaining / 100.0 +
                (if (e.hasPower) e.damagePerFrameVs(a) * 0.4 else 0.0) *
                (if (e.weaponAgainst(a).isSplash) 2.0 else 1.0)
    }

    private val defaultScorers = listOf(byEnemyType, byTimeToAttack, byTimeToKillEnemy, byEnemyRange, byEnemySpeed, byEnemyDamageCapabilities)

    fun bestCombatMoves(attackers: Collection<SUnit>, targets: Collection<SUnit>, aggressiveness: Double): List<CombatMove> {
        val relevantTargets = targets
                .filter { it.detected && it.unitType != UnitType.Zerg_Larva }
        if (relevantTargets.isEmpty() || attackers.isEmpty())
            return emptyList()

        val alreadyEngaged = relevantTargets.any { e -> attackers.any { e.inAttackRange(it, 48) || it.inAttackRange(e, 48) } } || relevantTargets.none { it.unitType.canAttack() }
        val averageMinimumDistanceToEnemy = if (alreadyEngaged) 0.0 else attackers.map { a -> relevantTargets.map { a.distanceTo(it) }.min()!! }.average()
        val pylons = 2 * (1 - fastSig(targets.count { it.unitType == UnitType.Protoss_Pylon }.toDouble()))
        val defensiveBuildings = targets.count {
            it.remainingBuildTime < 48 &&
                    (it.unitType == UnitType.Protoss_Photon_Cannon || it.unitType == UnitType.Protoss_Shield_Battery)
        }

        val pylonScorer: TargetScorer = { _, e, _ -> defensiveBuildings * pylons }
        val scorers = defaultScorers.asSequence() + pylonScorer

        return attackers.map { a ->
            val target = relevantTargets.asSequence()
                    .filter { a.hasWeaponAgainst(it) }
                    .maxBy { e ->
                        val result = scorers.sumByDouble { it(a, e, aggressiveness) }
//                        diag.log("combatmove scoring : $a to $e: ${scorers.map { it(a, e, aggressiveness) }.toList()} = $result")
                        result
                    }
            if (target != null) {
                if (a.distanceTo(target) < averageMinimumDistanceToEnemy * waitForReinforcementsTargetingFactor && target.unitType.canAttack())
                    WaitMove(a)
                else
                    AttackMove(a, target)
            } else
                Disengage(a)
        }
    }

    fun bestTarget(attacker: SUnit, targets: Collection<SUnit>, aggressiveNess: Double): SUnit? {
        val best = targets
                .filter {
                    it.detected &&
                            attacker.weaponAgainst(it) != WeaponType.None &&
                            it.unitType != UnitType.Zerg_Larva
                }.map { enemy ->
                    val sumByDouble = defaultScorers.sumByDouble { it(attacker, enemy, aggressiveNess) }
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

fun unitThreatValueToEnemy(unitType: UnitType): Int {
    if (unitType == UnitType.Terran_Bunker) {
        val bunkerWeapon = WeaponType.Gauss_Rifle
        val bunkerDPF = ((bunkerWeapon.damageAmount() * 4) / bunkerWeapon.damageCooldown().toDouble()).orZero()
        return (bunkerDPF * dpfThreatValueFactor).toInt()
    }
    val airWeapon = unitType.airWeapon()
    val airDPF = ((airWeapon.damageAmount() * unitType.maxAirHits()) / airWeapon.damageCooldown().toDouble()).orZero() *
            (if (airWeapon.isSplash) 2 else 1)
    val groundWeapon = unitType.groundWeapon()
    val groundDPF = ((groundWeapon.damageAmount() * unitType.maxGroundHits()) / groundWeapon.damageCooldown().toDouble()).orZero() *
            (if (groundWeapon.isSplash) 2 else 1)
    return (max(airDPF, groundDPF) * dpfThreatValueFactor).toInt()
}

fun agentHealthAndShieldValue(agent: Agent): Int =
        Simulator.HEALTH_AND_HALFED_SHIELD.applyAsInt(agent) / 12

fun unitTypeValue(unitType: UnitType) = when (unitType) {
    UnitType.Zerg_Drone, UnitType.Terran_SCV, UnitType.Protoss_Probe -> 11
    UnitType.Terran_Medic, UnitType.Protoss_High_Templar, UnitType.Terran_Science_Vessel -> 3
    UnitType.Terran_Vulture_Spider_Mine -> 0
    else -> 2
}

// Simplified, but should take more stuff into account:
// Basic idea: We might want to sacrifice units in order to gain global value. Ie. lose lings but kill workers in early game
val agentValueForPlayer = ToIntFunction<Agent> { agent ->
    val sUnit = agent.userObject as SUnit? ?: return@ToIntFunction 0
//    diag.log("agentValue ${sUnit} = ${unitThreatValueToEnemy(sUnit.unitType)}, ${agentHealthAndShieldValue(agent)}, ${unitTypeValue(sUnit.unitType)}")
    valueOfAgent(agent, sUnit.unitType)
}

fun valueOfAgent(agent: Agent, type: UnitType) =
        unitThreatValueToEnemy(type) +
                agentHealthAndShieldValue(agent) +
                unitTypeValue(type)