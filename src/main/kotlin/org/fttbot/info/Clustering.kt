package org.fttbot.info

import org.fttbot.div
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.Larva
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Spell
import org.openbw.bwapi4j.unit.Unit
import java.util.*
import kotlin.collections.set

class Cluster<U : Unit>(var position: Position, internal val units: MutableList<U>, private var aggPosition: Position = Position(0, 0)) {
    companion object {
        var clusters = listOf<Cluster<PlayerUnit>>()

        fun step() {
            val relevantUnits = (UnitQuery.myUnits + UnitQuery.enemyUnits + EnemyInfo.seenUnits)
                    .filter { it !is Larva && it !is Spell }
            clusters = dbscan(relevantUnits, 228, 3)
        }

        private fun <U : Unit> dbscan(units: List<U>, radius: Int, minPts: Int): List<Cluster<U>> {
            val noise = Cluster<U>(Position(0, 0), mutableListOf())
            val cluster = mutableMapOf<U, Cluster<U>>()
            val clusters = mutableListOf<Cluster<U>>()
            for (p in units) {
                if (cluster[p] != null) continue
                val n = units.inRadius(p, radius)
                if (n.size < minPts) {
                    noise.units += p
                    cluster[p] = noise
                    continue
                }

                val newCluster = Cluster<U>(Position(0, 0), mutableListOf())
                val s = ArrayDeque(n - p)
                while (!s.isEmpty()) {
                    val q = s.poll()
                    if (cluster[q] == noise) {
                        noise.units -= q
                        cluster[q] = newCluster
                    }
                    if (cluster[q] != null) continue
                    newCluster.units += q
                    cluster[q] = newCluster

                    val nn = units.inRadius(q, radius)
                    if (nn.size >= minPts) {
                        s += nn
                    }
                }
                if (newCluster.units.isNotEmpty()) clusters += newCluster
            }
            clusters.forEach {
                it.aggPosition = it.units.map { it.position }.reduce { acc, pos -> acc.add(pos) }
                it.position = it.aggPosition.div(it.units.size)
            }
            return clusters + noise.units.map { Cluster(it.position, mutableListOf(it)) }
        }
    }

    private class UnitInCluster<U : Unit>(var cluster: Cluster<U>, val unit: U)
}