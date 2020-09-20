package org.styx

import bwapi.Position
import bwapi.UnitType
import bwapi.WeaponType
import org.bk.ass.sim.Agent
import org.locationtech.jts.math.DD.EPS
import org.styx.Config.TargetEval.combatRelevancyFactor
import org.styx.Config.TargetEval.enemySpeedFactor
import org.styx.Config.TargetEval.evalFramesToKillFactor
import org.styx.Config.TargetEval.evalFramesToReach
import org.styx.Config.TargetEval.pylonFactor
import org.styx.Config.dpfThreatValueFactor
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
data class StayBack(override val unit: SUnit) : CombatMove

object TargetEvaluator {
    private val byEnemyType: TargetScorer = { _, e, _ ->
        when (e.unitType) {
            UnitType.Zerg_Egg, UnitType.Zerg_Lurker_Egg, UnitType.Zerg_Larva, UnitType.Protoss_Interceptor -> 0.0
            UnitType.Zerg_Extractor, UnitType.Terran_Refinery, UnitType.Protoss_Assimilator -> 0.01
            UnitType.Zerg_Drone, UnitType.Terran_SCV, UnitType.Protoss_Probe -> 0.02
            UnitType.Terran_Medic, UnitType.Protoss_High_Templar -> 0.05
            UnitType.Protoss_Carrier -> 0.10
            else -> 0.03
        }
    }

    private val byTimeToAttack: TargetScorer = { a, e, aggressiveness ->
        if (!a.hasPower) 0.0
        else {
            val framesToTurnTo = a.framesToTurnTo(e)
            1.0 - fastSig((max(0, a.distanceTo(e) - a.maxRangeVs(e)) / max(1.0, a.topSpeed) +
                    framesToTurnTo) * evalFramesToReach / aggressiveness)
        }
    }

    private val byTimeToKillEnemy: TargetScorer = { a, e, _ ->
        val damageAmount = a.weaponAgainst(e).damageAmount()
        1.0 - fastSig((e.hitPoints / (a.damagePerFrameVs(e) + EPS) - e.shields / (damageAmount + EPS)) * evalFramesToKillFactor)
    }

    private val byEnemySpeed: TargetScorer = { a, e, _ ->
        1.0 - fastSig(e.topSpeed * enemySpeedFactor)
    }

    private val defaultScorers = listOf(byEnemyType, byTimeToAttack, byTimeToKillEnemy, byEnemySpeed)

    fun bestCombatMoves(attackers: Collection<SUnit>, targets: Collection<SUnit>, aggressiveness: Double): List<CombatMove> {
        val relevantTargets = targets
                .filter { it.detected && it.unitType != UnitType.Zerg_Larva }
        if (relevantTargets.isEmpty() || attackers.isEmpty())
            return emptyList()
        val myAgents = attackers.map { unitToAgentMapper(it, attackers) }
        val enemyCombatRelevancy = relevantTargets.map { it to Styx.evaluator.evaluate(listOf(unitToAgentMapper(it, targets)), myAgents).value }.toMap()

        val alreadyEngaged = relevantTargets.any { e -> attackers.any { e.inAttackRange(it, 48) || it.inAttackRange(e, 48) } } || relevantTargets.none { it.unitType.canAttack() }
        val averageMinimumDistanceToEnemy = if (alreadyEngaged) 0.0 else attackers.map { a -> relevantTargets.map { a.distanceTo(it) }.min()!! }.average()
        val healthFactor: (SUnit) -> Double = { it.hitPoints.toDouble() / it.unitType.maxHitPoints() + (it.shields / it.unitType.maxShields().toDouble()).orZero() / 4 }
        val averageHealth = attackers.map(healthFactor).average()
        val pylonFactor = pylonFactor * (1 - fastSig(targets.count { it.unitType == UnitType.Protoss_Pylon }.toDouble()))

        val attackerCount = mutableMapOf<SUnit, Int>()
        val pylonScorer: TargetScorer = { _, e, _ -> if (e.unitType == UnitType.Protoss_Pylon) pylonFactor else 0.0 }
        val overloadAvoidance: TargetScorer = { _, e, _ -> 1.0 - fastSig(attackerCount.getOrDefault(e, 0) * 0.01) }
        val combatRelevancy: TargetScorer = { _, e, _ -> fastSig(enemyCombatRelevancy[e]!! * combatRelevancyFactor) }
        val scorers = defaultScorers.asSequence() + pylonScorer + overloadAvoidance + combatRelevancy

        return attackers.map { a ->
            val target = relevantTargets.asSequence()
                    .filter { a.hasWeaponAgainst(it) }
                    .maxBy { e ->
                        val result = scorers.sumByDouble { it(a, e, aggressiveness) }
//                        diag.log("combatmove scoring : $a to $e: ${scorers.map { it(a, e, aggressiveness) }.toList()}, $overloadAvoidance = $result", Level.WARNING)
                        require(!result.isNaN())
                        result
                    }
            if (target != null) {
                if (!alreadyEngaged && healthFactor(a) * 2.8 < averageHealth || healthFactor(a) * 4 < averageHealth && a.engaged.isNotEmpty()) {
                    StayBack(a)
                } else if (a.distanceTo(target) < averageMinimumDistanceToEnemy * waitForReinforcementsTargetingFactor)
                    WaitMove(a)
                else {
                    attackerCount.compute(target) { _, count -> if (count == null) 0 else count + 1 }
                    AttackMove(a, target)
                }
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
            (if (airWeapon.isSplash) Config.splashValueFactor else 1.0) *
            (1 + airWeapon.maxRange() * Config.rangeValueFactor)
    val groundWeapon = unitType.groundWeapon()
    val groundDPF = ((groundWeapon.damageAmount() * unitType.maxGroundHits()) / groundWeapon.damageCooldown().toDouble()).orZero() *
            (if (groundWeapon.isSplash) Config.splashValueFactor else 1.0) *
            (1 + groundWeapon.maxRange() * Config.rangeValueFactor)

    return (max(airDPF, groundDPF) * dpfThreatValueFactor).toInt()
}

fun agentHealthAndShieldValue(agent: SUnit): Int =
        (agent.hitPoints + agent.shields / 2) / 15

fun unitTypeValue(unitType: UnitType) = when (unitType) {
    UnitType.Protoss_Carrier, UnitType.Zerg_Drone, UnitType.Terran_SCV, UnitType.Protoss_Probe -> 27
    UnitType.Terran_Medic, UnitType.Protoss_High_Templar, UnitType.Terran_Science_Vessel -> 15
    UnitType.Terran_Vulture_Spider_Mine -> 0
    UnitType.Terran_Bunker -> 90
    else -> 8
}

// Simplified, but should take more stuff into account:
// Basic idea: We might want to sacrifice units in order to gain global value. Ie. lose lings but kill workers in early game
val agentValueForPlayer = ToIntFunction<Agent> { agent ->
    val sUnit = agent.userObject as SUnit? ?: return@ToIntFunction 0
//    diag.log("agentValue ${sUnit} = ${unitThreatValueToEnemy(sUnit.unitType)}, ${agentHealthAndShieldValue(agent)}, ${unitTypeValue(sUnit.unitType)}")
    valueOfUnit(sUnit)
}

fun valueOfUnit(unit: SUnit): Int {
    return unitThreatValueToEnemy(unit.unitType) +
            agentHealthAndShieldValue(unit) +
            unitTypeValue(unit.unitType)
}