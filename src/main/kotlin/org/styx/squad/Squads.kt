package org.styx.squad

import bwapi.Position
import org.bk.ass.collection.UnorderedCollection
import org.bk.ass.sim.Agent
import org.bk.ass.sim.AttackerBehavior
import org.styx.*
import org.styx.Styx.evaluator
import kotlin.math.max

val adjectives = listOf("stinging", "red", "running", "weak", "blazing", "awful", "spiteful", "loving", "hesitant", "raving", "hunting")
val nouns = listOf("squirrel", "bee", "scorpion", "coyote", "rabbit", "marine-eater", "void", "space-cowboy", "wallaby")

class Squad {
    val name = "${adjectives.random()} ${nouns.random()}"
    var task = ""
    var fastEval: Double = 0.5
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

    override fun toString(): String = "$name: [$fastEval, $mine, $enemies, $myCenter, $center]"

    fun update(units: List<SUnit>) {
        mine = units.filter { it.myUnit }
        enemies = units.filter { it.enemyUnit }
        all = units
        fastEval = evaluator.evaluate(
                mine.filter { it.remainingBuildTime < 48 }.map { it.agent() },
                enemies.filter { it.remainingBuildTime < 48 }.map { it.agent() })
    }
}

class NoAttackIfGatheringBehavior : AttackerBehavior() {
    override fun simUnit(frameSkip: Int, agent: Agent, allies: UnorderedCollection<Agent>, enemies: UnorderedCollection<Agent>): Boolean =
            (agent.userObject as? SUnit)?.gathering != true && super.simUnit(frameSkip, agent, allies, enemies)
}