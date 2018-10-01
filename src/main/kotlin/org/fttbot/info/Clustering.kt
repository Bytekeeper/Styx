package org.fttbot.info

import com.badlogic.gdx.math.ConvexHull
import org.fttbot.LazyOnFrame
import org.fttbot.div
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.plus
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.Larva
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Spell
import java.util.*
import kotlin.collections.set

val PlayerUnit.myCluster get() = Cluster.clusterOf.clusters[this] ?: ClusterSet.NOISE

class Cluster<U : PlayerUnit>(var position: Position, internal val units: MutableList<U>) {
    val attackEval by LazyOnFrame {
        CombatEval.bestProbilityToWin(mySimUnits.filter { it.isAttacker },
                enemySimUnits.filter { it.isAttacker }, 0.6, 0.8f)
    }

    val mySimUnits by LazyOnFrame {
        myUnits.map(SimUnit.Companion::of)
    }

    val enemySimUnits by LazyOnFrame {
        enemyUnits.map(SimUnit.Companion::of)
    }

    val myUnits by LazyOnFrame {
        units.filter { it.isMyUnit }
    }

    val enemyUnits by LazyOnFrame {
        units.filter { it.isEnemyUnit }
    }

    val enemyHull by LazyOnFrame {
        val floatArray = FloatArray(enemyUnits.size * 2) { i ->
            val pos = enemyUnits[i / 2].position
            (if (i % 2 == 0) pos.x else pos.y).toFloat()
        }
        val polygon = convexHull.computePolygon(floatArray, false)
        polygon.items.asSequence()
                .take(polygon.size)
                .windowed(2, 2, false) { p -> Position(p[0].toInt(), p[1].toInt()) }
                .toList()
    }

    companion object {
        private val convexHull = ConvexHull()
        var clusterOf = ClusterSet<PlayerUnit>()
        val clusters by LazyOnFrame {
            clusterOf.clusters.values.toSet()
        }

        private var squadClusters = ClusterSet<PlayerUnit>()
        val squads by LazyOnFrame {
            squadClusters.clusters.values.toSet()
        }

        fun step() {
            val relevantUnits = (UnitQuery.ownedUnits + EnemyInfo.seenUnits)
                    .filter { it !is Larva && it !is Spell }
            if (clusterOf.completed) {
                clusterOf.restart(relevantUnits, { ((it as? Attacker)?.maxRange() ?: 0) + 64 }, 3)
            }
            clusterOf.step()
            if (squadClusters.completed) {
                squadClusters.restart(UnitQuery.myUnits.filter { it is Attacker }, { 128 }, 3)
            }
            squadClusters.step()
        }
    }
}

class ClusterSet<U : PlayerUnit>() {
    var clusters = mutableMapOf<U, Cluster<U>>()
        private set
    private var workingSet = mutableMapOf<U, Cluster<U>>()
    var completed = true
        private set

    private var db: List<U> = emptyList()
    private var radius: (U) -> Int = { 0 }
    private var minPts = 10000
    private val processed = mutableSetOf<U>()
    private var radiusCache: RadiusCache<U> = RadiusCache(emptyList())
    private var it: Iterator<U> = db.iterator()
    private val noise = Cluster<U>(Position(0, 0), mutableListOf())

    companion object {
        val NOISE = Cluster(Position(0, 0), mutableListOf())
    }

    fun restart(db: List<U>, radius: (U) -> Int, minPts: Int) {
        this.db = db.sortedByDescending { radius(it) }
        this.radiusCache = RadiusCache(db)
        this.radius = radius
        this.minPts = minPts
        processed.clear()
        this.it = this.db.iterator()
        completed = false
        workingSet = clusters.values.distinct().map { Cluster(it.position, it.units.toMutableList()) }
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
            val n = radiusCache.inRadius(p, radius(p))
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
                val qn = radiusCache.inRadius(q, radius(q))
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
            noise.units.forEach { workingSet.put(it, Cluster(it.position, mutableListOf(it))) }
            noise.units.clear()
            completed = true
            clusters = workingSet
        }
    }
}
