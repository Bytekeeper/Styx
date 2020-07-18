package org.styx.squad

import bwapi.Position
import bwapi.UnitType
import org.bk.ass.sim.Agent
import org.bk.ass.sim.Evaluator
import org.styx.*
import org.styx.Styx.evaluator
import org.styx.Styx.map
import kotlin.math.max

val adjectives = listOf("stinging", "red", "running", "weak", "blazing", "awful", "spiteful", "loving", "hesitant", "raving", "hunting")
val nouns = listOf("squirrel", "bee", "scorpion", "coyote", "rabbit", "marine-eater", "void", "space-cowboy", "wallaby")

class SquadBoard {
    val name = "${adjectives.random()} ${nouns.random()}"
    var task = ""
    var fastEval: Evaluator.EvaluationResult = Evaluator.EVAL_NO_COMBAT
    var mine = emptyList<SUnit>()
    var enemies = emptyList<SUnit>()
    var all = emptyList<SUnit>()
    val myCenter by LazyOnFrame {
        mine.fold(Position(0, 0)) { agg, p -> agg + p.position } / max(1, mine.size)
    }
    val center by LazyOnFrame {
        all.fold(Position(0, 0)) { agg, p -> agg + p.position } / max(1, all.size)
    }
    val myWorkers by lazy { mine.filter { it.unitType.isWorker } }
    var shouldBeDefended: Boolean = false
        private set

    override fun toString(): String = "$name: [$fastEval, $mine, $enemies, $myCenter, $center]"

    fun update(units: List<SUnit>) {
        mine = units.filter { it.myUnit }
        enemies = units.filter { it.enemyUnit }
        all = units
        shouldBeDefended = mine.any { it.unitType.isBuilding }
        fastEval = evaluator.evaluate(
                mine.filter { it.remainingBuildTime < 48 }.map { it.agent() },
                enemies.filter { it.remainingBuildTime < 48 }.map { it.agent() })
    }

    fun stockUpUtility(myAdditionalUnits: List<SUnit>, enemyAdditionalUnits: List<SUnit>): Double {
        val distanceToEnemyBase = 1 - fastSig(Styx.bases.enemyBases.map { it.center.getDistance(center) }.min(0.0))
        val distanceToSquad = 1 - fastSig(myAdditionalUnits.map {
            if (it.flying) it.distanceTo(myCenter) else map.getPathLength(it.position, myCenter)
        }.average())
        val mergedEval = evaluator.evaluate((mine + myAdditionalUnits).distinct().map { it.agent() },
                (enemies + enemyAdditionalUnits).distinct().map { it.agent() })
        val evalDelta = fastSig((mergedEval.value - fastEval.value + 1.0) * Config.seekSquadEvalFactor)
        val enemyValue = fastSig(enemies.sumBy { valueOfUnit(it) }.toDouble() * Config.seekSquadEvalFactor / 10.0)
        return (distanceToEnemyBase + distanceToSquad + evalDelta + enemyValue) / 4
    }
}