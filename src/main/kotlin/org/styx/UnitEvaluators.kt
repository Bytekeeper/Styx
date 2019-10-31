package org.styx

import bwapi.Position
import bwapi.UnitType
import bwapi.WeaponType
import org.bk.ass.sim.Agent
import org.bk.ass.sim.Simulator
import java.util.*
import java.util.function.ToIntFunction
import kotlin.math.max

typealias TargetScorer = (a: SUnit, e: SUnit) -> Double

interface CombatMove
data class AttackMove(val enemy: SUnit) : CombatMove

object TargetEvaluator {
    private val byEnemyType: TargetScorer = { _, e ->
        when (e.unitType) {
            UnitType.Zerg_Egg, UnitType.Zerg_Lurker_Egg -> -5.0
            UnitType.Zerg_Larva -> -10.0
            UnitType.Zerg_Extractor, UnitType.Terran_Refinery, UnitType.Protoss_Assimilator -> -2.0
            else -> 0.0
        }
    }

    private val byTimeToAttack: TargetScorer = { a, e ->
        val framesToTurnTo = a.framesToTurnTo(e)
        -max(0, a.distanceTo(e) - a.maxRangeVs(e)) / 5.0 / max(1.0, a.topSpeed) -
                framesToTurnTo / 3.0
    }

    private val byEnemyStatus: TargetScorer = { _, e ->
        -e.hitPoints / 500.0 - e.shields / 700.0
    }

    private val byEnemyRange: TargetScorer = { _, e ->
        e.unitType.groundWeapon().maxRange() / 300.0 +
                e.unitType.airWeapon().maxRange() / 400.0
    }

    private val byDamageToEnemy: TargetScorer = { a, e ->
        a.damagePerFrameVs(e) * 0.1
    }

    private val byEnemyDamageCapabilities: TargetScorer = { a, e ->
        e.damagePerFrameVs(a) * 0.04
    }

    private val scorers = listOf(byEnemyType, byTimeToAttack, byEnemyStatus, byEnemyRange, byDamageToEnemy, byEnemyDamageCapabilities)

    fun bestCombatMoves(attackers: Collection<SUnit>, targets: Collection<SUnit>): Map<SUnit, CombatMove?> {
        val relevantTargets = targets
                .filter { it.detected && it.unitType != UnitType.Zerg_Larva}
        if (relevantTargets.isEmpty())
            return mapOf()
        val remaining = ArrayDeque(attackers)
        val result = mutableMapOf<SUnit, CombatMove>()
        val attackedByCount = mutableMapOf<SUnit, Int>()
        while (remaining.isNotEmpty()) {
            val best = remaining.mapNotNull { a ->
                relevantTargets.filter { a.weaponAgainst(it) != WeaponType.None }.map { e ->
                    e to scorers.sumByDouble { it(a, e) } - (attackedByCount[e] ?: 0) * 0.05
                }.maxBy { it.second }?.let { a to it }
            }.maxBy { it.second.second } ?: break
            remaining.remove(best.first)
            result[best.first] = AttackMove(best.second.first)
            attackedByCount.compute(best.second.first) { _, v -> (v ?: 0) + 1 }
        }
        return result
    }

    fun bestTarget(attacker: SUnit, targets: Collection<SUnit>): SUnit? {
        val best= targets
                .filter {
                    it.detected &&
                            attacker.weaponAgainst(it) != WeaponType.None &&
                            it.unitType != UnitType.Zerg_Larva
                }.map { enemy ->
                    val sumByDouble = scorers.sumByDouble { it(attacker, enemy) }
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
            (if (u.carrying) 80.0 else 0.0)
}

// Simplified, but should take more stuff into account:
// Basic idea: We might want to sacrifice units in order to gain global value. Ie. lose lings but kill workers in early game
val agentValueForPlayer = ToIntFunction<Agent> { agent ->
    val unitType = (agent.userObject as? SUnit)?.unitType ?: return@ToIntFunction 0
    val airDPF = ((unitType.airWeapon().damageAmount() * unitType.maxAirHits()) / unitType.airWeapon().damageCooldown().toDouble()).orZero()
    val groundDPF = ((unitType.groundWeapon().damageAmount() * unitType.maxGroundHits()) / unitType.groundWeapon().damageCooldown().toDouble()).orZero()
    (max(airDPF, groundDPF) * 3 +
            Simulator.HEALTH_AND_HALFED_SHIELD.applyAsInt(agent) / 3).toInt()
}
