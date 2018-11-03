package org.fttbot.info

import org.bk.ass.Agent
import org.bk.ass.Simulator
import org.bk.ass.cluster.StableDBScanner
import org.bk.ass.query.PositionAndId
import org.bk.ass.query.UnitFinder
import org.fttbot.FTTBot.agentFactory
import org.fttbot.FTTBot.geometryFactory
import org.fttbot.LazyOnFrame
import org.locationtech.jts.geom.Coordinate
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.Larva
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Spell

val PlayerUnit.myCluster get() = Cluster.clusterOf.getClusterOf(this).userObject as Cluster<PlayerUnit>

class Cluster<U : PlayerUnit>(var position: Position, internal val units: MutableList<U>) {

    lateinit var combatPerformance: Pair<Double, Double>

    lateinit var attackSim: Pair<Collection<Agent>, Collection<Agent>>
        private set

    lateinit var mySimUnits: List<Agent>
        private set

    lateinit var enemySimUnits: List<Agent>
        private set

    lateinit var myUnits: List<U>
        private set

    lateinit var enemyUnits: List<U>
        private set

    val enemyHull by LazyOnFrame {
        val points = enemyUnits.filter { it.isCombatRelevant() }
                .map { e -> Coordinate(e.position.x.toDouble(), e.position.y.toDouble()) }
                .toTypedArray()
        geometryFactory.createMultiPointFromCoords(points).convexHull()
    }

    val enemyHullWithBuffer by LazyOnFrame {
        enemyHull.buffer(400.0, 3)
    }

    internal fun step() {
        if (units.isNotEmpty()) position = units.asSequence().map { it.position }.reduce { acc, p -> acc.add(p) }.divide(Position(units.size, units.size))
        myUnits = units.filter { it.isMyUnit }
        enemyUnits = units.filter { it.isEnemyUnit }
        val myRelevantUnits = myUnits.filter { it.isCombatRelevant() }
        mySimUnits = myRelevantUnits.map { agentFactory.of(it) }
        val relevantEnemyUnits = enemyUnits.filter { it.isCombatRelevant() }
        enemySimUnits = relevantEnemyUnits.map { agentFactory.of(it) }
        simulator.resetUnits()
        myRelevantUnits.forEach { simulator.addAgentA(agentFactory.of(it)) }
        relevantEnemyUnits.forEach { simulator.addAgentB(agentFactory.of(it)) }
        simulator.simulate(6 * 24)
        attackSim = simulator.agentsA to simulator.agentsB
        combatPerformance = combatPerformance(simulator.agentsA, myRelevantUnits) to combatPerformance(simulator.agentsB, relevantEnemyUnits)
    }


    companion object {
        private val simulator = Simulator()
        internal val clusterOf = StableDBScanner<PlayerUnit>(2)

        val clusters by LazyOnFrame {
            clusterOf.clusters
        }
        val squadClusters = StableDBScanner<PlayerUnit>(3)

        fun combatPerformance(agents: MutableCollection<Agent>, units: List<PlayerUnit>) =
                agents.sumBy { it.health + it.shields } / (0.1 + units.sumBy { if (it.isCombatRelevant()) (it.hitPoints + it.shields) else 0 })


        fun step() {
            updateCombatClusters()
            updateSquads()
            simulator.resetCollisionMap()
            (clusterOf.clusters + squadClusters.clusters).forEach { c ->
                val cluster = c.userObject as? Cluster<PlayerUnit> ?: kotlin.run {
                    val result = Cluster<PlayerUnit>(Position(0, 0), c.elements)
                    c.userObject = result
                    result
                }
                cluster.step()
            }
        }

        private fun updateSquads() {
            val relevantUnits = (UnitQuery.myUnits)
                    .filter { it !is Larva && it !is Spell && it.isCombatRelevant() }
            val finder = UnitFinder(relevantUnits) { u -> PositionAndId(u.id, u.x, u.y) }
            squadClusters.updateDB(relevantUnits) { u -> finder.inRadius(u, 180) }.scan(-1)
        }

        private fun updateCombatClusters() {
            val relevantUnits = (UnitQuery.ownedUnits + EnemyInfo.seenUnits)
                    .filter { it !is Larva && it !is Spell }
            val finder = UnitFinder(relevantUnits) { u -> PositionAndId(u.id, u.x, u.y) }
            clusterOf.updateDB(relevantUnits) { u -> finder.inRadius(u, 400) }.scan(-1)
        }
    }
}