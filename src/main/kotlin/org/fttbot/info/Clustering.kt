package org.fttbot.info

import org.bk.ass.BWAPI4JAgentFactory
import org.bk.ass.Simulator
import org.fttbot.LazyOnFrame
import org.fttbot.div
import org.fttbot.estimation.CombatEval
import org.fttbot.plus
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.Larva
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Spell
import java.util.*
import kotlin.collections.set

val PlayerUnit.myCluster get() = Cluster.clusterOf.clusters[this] ?: ClusterSet.NOISE

class Cluster<U : PlayerUnit>(var position: Position, internal val units: MutableList<U>, internal val source: Cluster<U>? = null) {

    val attackEval by LazyOnFrame {
        val enemies = enemySimUnits.filter {
            val playerUnit = it.userObject as PlayerUnit
            playerUnit.isCombatRelevant()
        }
        val myMob = mySimUnits.filter {
            val playerUnit = it.userObject as PlayerUnit
            playerUnit.isCombatRelevant()
        }
        if (enemies.isEmpty()) myMob to 1.0
        else CombatEval.bestProbilityToWin(myMob,
                enemies, 0.6, 0.9f)
    }

    val attackSim by LazyOnFrame {
        val enemies = enemySimUnits.filter { (it.userObject as PlayerUnit).isCombatRelevant() }
        val myMob = mySimUnits.filter { (it.userObject as PlayerUnit).isCombatRelevant() }
        simulator.reset()
        myMob.forEach { simulator.addAgentA(it) }
        enemies.forEach { simulator.addAgentB(it) }
        simulator.simulate()
        simulator.agentsA to simulator.agentsB
    }

    val mySimUnits by LazyOnFrame {
        myUnits.map { factory.of(it) }
    }

    val enemySimUnits by LazyOnFrame {
        enemyUnits.map { factory.of(it) }
    }

    val myUnits by LazyOnFrame {
        units.filter { it.isMyUnit }
    }

    val enemyUnits by LazyOnFrame {
        units.filter { it.isEnemyUnit }
    }

    val enemyHull by LazyOnFrame {
        val points = enemyUnits.map { e -> Coordinate(e.position.x.toDouble(), e.position.y.toDouble()) }.toTypedArray()
        geometryFactory.createMultiPointFromCoords(points).convexHull()
    }

    val enemyHullWithBuffer by LazyOnFrame {
        enemyHull.buffer(400.0, 3)
    }

    companion object {
        private val factory = BWAPI4JAgentFactory()
        private val simulator = Simulator()
        private val geometryFactory = GeometryFactory()

        var clusterOf = ClusterSet<PlayerUnit>()
        val clusters by LazyOnFrame {
            clusterOf.clusters.values.toSet()
        }

        fun step() {
            if (clusterOf.completed) {
                val relevantUnits = (UnitQuery.ownedUnits + EnemyInfo.seenUnits)
                        .filter { it !is Larva && it !is Spell }
                clusterOf.restart(relevantUnits, { ((it as? Attacker)?.maxRange() ?: 0) + 64 }, 3)
            }
            clusterOf.step()
        }
    }
}

class ClusterSet<U : PlayerUnit>() {
    var clusters = mapOf<U, Cluster<U>>()
        private set
    private var workingSet = mutableMapOf<U, Cluster<U>>()
    var completed = true
        private set

    private var db: List<U> = emptyList()
    private var radius: (U) -> Int = { 0 }
    private var minPts = 10000
    private val processed = mutableSetOf<U>()
    private var unitFinder: MyUnitFinder<U> = MyUnitFinder(emptyList())
    private var it: Iterator<U> = db.iterator()
    private val noise = Cluster<U>(Position(0, 0), mutableListOf())

    companion object {
        val NOISE = Cluster(Position(0, 0), mutableListOf())
    }

    fun restart(db: List<U>, radius: (U) -> Int, minPts: Int) {
        this.db = db.sortedByDescending { radius(it) }
        this.unitFinder = MyUnitFinder(db)
        this.radius = radius
        this.minPts = minPts
        processed.clear()
        this.it = this.db.iterator()
        completed = false
        workingSet = clusters.values.distinct().map { Cluster(it.position, it.units.toMutableList(), it) }
                .flatMap { c -> c.units.map { it to c } }
                .toMap().toMutableMap()
    }

    fun step() {
        if (completed) return
        fun U.setCluster(c: Cluster<U>) {
            workingSet[this]?.units?.remove(this)
            workingSet[this] = c
            c.units += this
            processed.add(this)
        }

        fun U.cluster() = workingSet[this]
        fun U.isProcessed() = processed.contains(this)

        var i = 20;

        while (it.hasNext() && i > 0) {
            i--
            val p = it.next()
            if (p.isProcessed())
                continue
            val n = unitFinder.inRadius(p, radius(p))
            if (n.size < minPts) {
                p.setCluster(noise)
                continue
            }
            val c = (if (p.cluster() !== noise) p.cluster() else null)
                    ?: Cluster<U>(p.position, mutableListOf())
            workingSet.keys.removeAll(c.units)
            c.units.clear()
            p.setCluster(c)
            val s = ArrayDeque(n - p)

            while (s.isNotEmpty()) {
                val q = s.removeLast()
                if (q.cluster() === noise) q.setCluster(c)
                if (q.isProcessed()) continue
                q.setCluster(c)
                val qn = unitFinder.inRadius(q, radius(q))
                if (qn.size >= minPts) {
                    s += qn
                }
            }
        }
        if (!it.hasNext()) {
            workingSet.values.removeIf {
                it.units.retainAll(db)
                it.units.isEmpty()
            }
            workingSet.values.distinct().forEach {
                val aggPosition = it.units.fold(Position(0, 0)) { acc, u -> acc + u.position }
                it.position = aggPosition / it.units.size
            }
            noise.units.forEach {
                require(workingSet[it] == noise)
                workingSet[it] = Cluster(it.position, mutableListOf(it))
            }
            noise.units.clear()
            completed = true
            clusters = workingSet.map { entry ->
                val source = entry.value.source
                if (source == null)
                    entry.toPair()
                else {
                    source.units.clear()
                    source.units.addAll(entry.value.units)
                    source.position = entry.value.position
                    entry.key to source
                }
            }.toMap()
        }
    }
}
