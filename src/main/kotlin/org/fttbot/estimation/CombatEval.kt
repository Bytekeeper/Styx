package org.fttbot.estimation

import com.badlogic.gdx.math.Vector2
import org.bk.ass.Agent
import org.bk.ass.Evaluator
import org.fttbot.fastsig
import org.fttbot.minus
import org.fttbot.search.EPS
import org.fttbot.toPosition
import org.fttbot.toVector
import org.openbw.bwapi4j.type.*
import org.openbw.bwapi4j.unit.Unit
import kotlin.math.max

const val MAX_FRAMES_TO_ATTACK = 24

object CombatEval {
    private val evaluator = Evaluator()

//    fun attackScore(attackerSim: Agent, enemySim: Agent): Double {
//        val wpn = attackerSim.determineWeaponAgainst(enemySim)
//        if (wpn.type() == WeaponType.None || !enemySim.detected) return -100000.0;
//        val enemyWpn = enemySim.determineWeaponAgainst(attackerSim)
//        val futurePosition = enemySim.position?.add(enemySim.velocity.scl(24f * 3).toPosition())
//        val futureDistance = futurePosition?.minus(attackerSim.position
//                ?: futurePosition)?.toVector()?.len() ?: 30f
//        return (enemySim.hitPoints + enemySim.shield) / max(attackerSim.damagePerFrameTo(enemySim), 0.001) +
//                0.5 * (attackerSim.hitPoints + attackerSim.shield) / max(enemySim.damagePerFrameTo(attackerSim), 0.001) +
//                (if (enemySim.type == UnitType.Zerg_Larva || enemySim.type == UnitType.Zerg_Egg || enemySim.type == UnitType.Protoss_Interceptor || enemySim.type.isAddon) 25000 else 0) +
//                (if (enemySim.type.isWorker || enemySim.canHeal || enemySim.canRepair) -150 else 0) +
//                (if (enemySim.type.spaceProvided() > 0) -200 else 0) +
//                (if (attackerSim.isAir) 0.0 else 1.0 * futureDistance) +
//                1.5 * (enemySim.position?.getDistance(attackerSim.position) ?: 0) +
//                (if (attackerSim.hasSplashWeapon) -100 else 0) +
//                (fastsig(max(0.0, futureDistance.toDouble() - enemyWpn.maxRange())) * -100 *
//                        (if (enemySim.type == UnitType.Zerg_Lurker && enemySim.isBurrowed) 20.0 else 1.0)) +
//                (if (enemySim.type == UnitType.Protoss_Carrier) -300 else 0) +
//                (fastsig(max(0.0, futureDistance.toDouble() - wpn.maxRange())) * 150 *
//                        (if (attackerSim.type == UnitType.Zerg_Lurker && attackerSim.isBurrowed) 20.0 else 1.0)) +
//                (enemySim.topSpeed - attackerSim.topSpeed) * 30
//    }

    fun minAmountOfAdditionalsForProbability(myUnits: List<Agent>, additionalUnits: Agent, enemies: List<Agent>, minProbability: Double = 0.6): Int {
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

    fun bestEnemyToKill(unitsOfPlayerA: List<Agent>, unitsOfPlayerB: List<Agent>, fightingPosition: Float = 0.5f): Agent? =
            unitsOfPlayerB.maxBy {
                probabilityToWin(unitsOfPlayerA, unitsOfPlayerB - it, fightingPosition)
            }

    fun bestProbilityToWin(unitsOfPlayerA: List<Agent>, unitsOfPlayerB: List<Agent>, minProbabilityToAchieve: Double = 0.8, fightingPosition: Float = 0.5f): Pair<List<Agent>, Double> {
        var best = unitsOfPlayerA
        var bestEval = probabilityToWin(best, unitsOfPlayerB)
        val typesRemaining = best.map { (it.userObject as Unit).type }.toMutableSet()
        while (!typesRemaining.isEmpty() && bestEval < minProbabilityToAchieve) {
            val bestType = typesRemaining.map { toTest -> toTest to probabilityToWin(best.filter { (it.userObject as Unit) != toTest }, unitsOfPlayerB, fightingPosition) }
                    .maxBy { it.second }!!
            if (bestType.second < bestEval)
                break
            typesRemaining.remove(bestType.first)
            bestEval = bestType.second
            best = best.filter { (it.userObject as Unit) != bestType.first }
        }
        return best to bestEval
    }

    fun probabilityToWin(unitsOfPlayerA: List<Agent>, unitsOfPlayerB: List<Agent>, fightingPosition: Float = 0.5f, fallbackDistance: Double = 64.0): Double {
        return evaluator.evaluate(unitsOfPlayerA, unitsOfPlayerB)
    }
}

