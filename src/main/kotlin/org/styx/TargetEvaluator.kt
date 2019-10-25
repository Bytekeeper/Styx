package org.styx

import bwapi.UnitType
import bwapi.WeaponType
import java.util.*
import kotlin.math.max

typealias TargetScorer = (a: SUnit, e: SUnit) -> Double

object TargetEvaluator {
    private val byEnemyType: TargetScorer = { _, e ->
        when (e.unitType) {
            UnitType.Zerg_Egg, UnitType.Zerg_Lurker_Egg -> -5.0
            UnitType.Zerg_Larva -> -10.0
            UnitType.Zerg_Extractor, UnitType.Terran_Refinery, UnitType.Protoss_Assimilator -> -2.0
            else -> 0.0
        }
    }

    private val byAttackerRange: TargetScorer = { a, e ->
        -a.distanceTo(e) / 30.0 / max(5.0, (a.maxRangeVs(e) + 3 * a.topSpeed))
    }

    private val byEnemyStatus: TargetScorer = { _, e ->
        -e.hitPoints / 600.0 - e.shields / 900.0
    }

    private val byEnemyRange: TargetScorer = { _, e ->
        e.unitType.groundWeapon().maxRange() / 200.0 +
                e.unitType.airWeapon().maxRange() / 300.0
    }

    private val byDamageToEnemy: TargetScorer = { a, e ->
        a.damagePerFrameVs(e) * 0.1
    }

    private val byEnemyDamageCapabilities: TargetScorer = { a, e ->
        e.damagePerFrameVs(a) * 0.06
    }

    private val scorers = listOf(byEnemyType, byAttackerRange, byEnemyStatus, byEnemyRange, byDamageToEnemy, byEnemyDamageCapabilities)

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
                    e to scorers.sumByDouble { it(a, e) } - (attackedByCount[e] ?: 0) * 0.1
                }.maxBy { it.second }?.let { a to it }
            }.maxBy { it.second.second } ?: break
            remaining.remove(best.first)
            result[best.first] = AttackMove(best.second.first)
            attackedByCount.compute(best.second.first) { _, v -> (v ?: 0) + 1 }
        }
        if (targets.any { it.unitType.isBuilding} && result.values.any { (it as? AttackMove)?.enemy?.unitType == UnitType.Zerg_Egg }) {
            println("!")
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

interface CombatMove
data class AttackMove(val enemy: SUnit) : CombatMove