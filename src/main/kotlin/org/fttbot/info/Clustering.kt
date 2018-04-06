package org.fttbot.info

import org.fttbot.div
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit

class Cluster<U : Unit>(var position: Position, internal val units: MutableSet<U>, private var aggPosition: Position = Position(0,0), internal var lastUnitCount: Int = 1) {
    companion object {
        var mobileCombatUnits: List<Cluster<MobileUnit>> = emptyList()
        var myClusters: List<Cluster<PlayerUnit>> = emptyList()
        var enemyClusters: List<Cluster<PlayerUnit>> = emptyList()

        fun getClusterOf(unit: PlayerUnit) = mobileCombatUnits.firstOrNull { it.units.contains(unit) }

        fun step() {
            mobileCombatUnits = buildClusters(UnitQuery.myUnits.filterIsInstance(MobileUnit::class.java)
                    .filter { it.isCompleted && it !is Worker && it is Attacker})
            myClusters = buildClusters(UnitQuery.myUnits)
            enemyClusters = buildClusters(UnitQuery.enemyUnits + EnemyInfo.seenUnits)
        }

        private fun <U : Unit> buildClusters(relevantUnits: List<U>): List<Cluster<U>> {
            val clusters: MutableList<Cluster<U>> = ArrayList()
            val clusterUnits = relevantUnits.map { unit ->
                val cluster = clusters.filter { it.position.getDistance(unit.position) < 300 }
                        .maxBy { it.units.size }
                if (cluster != null) {
                    cluster.units += unit
                    cluster.aggPosition = cluster.aggPosition.add(unit.position)
                    UnitInCluster(cluster, unit)
                } else {
                    val newCluster = Cluster(unit.position, mutableSetOf(unit), unit.position)
                    clusters.add(newCluster)
                    UnitInCluster(newCluster, unit)
                }
            }
            var maxTries = 6
            do {
                clusters.forEach {
                    val size = it.units.size
                    it.position = it.aggPosition.div(size)
                    it.lastUnitCount = size
                    it.units.clear()
                    it.aggPosition = Position(0, 0)
                }
                val changes = clusterUnits.count { cu ->
                    val unit = cu.unit
                    val cluster = clusters.filter { it.position.getDistance(unit.position) < 300 }.reversed().maxBy { it.lastUnitCount }
                    if (cluster != null) {
                        cluster.aggPosition = cluster.aggPosition.add(unit.position)
                        cluster.units += unit
                        if (cu.cluster != cluster) {
                            cu.cluster = cluster
                            true
                        } else false
                    } else {
                        val newCluster = Cluster(unit.position, mutableSetOf(unit), unit.position)
                        clusters.add(newCluster)
                        cu.cluster = newCluster
                        true
                    }
                }
                clusters.removeIf { it.units.isEmpty() }
            } while (changes > 0 && maxTries-- > 0)
            return clusters.toList()
        }
    }

    private class UnitInCluster<U : Unit>(var cluster: Cluster<U>, val unit: U)
}