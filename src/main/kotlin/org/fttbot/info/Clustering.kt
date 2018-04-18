package org.fttbot.info

import org.fttbot.div
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit

class Cluster<U : Unit>(var position: Position, internal val units: MutableList<U>, private var aggPosition: Position = Position(0, 0), internal var lastUnitCount: Int = 1) {
    val byType: Map<UnitType, List<U>> by lazy {
        units.groupBy { it.initialType }
    }

    companion object {
        val mobileCombatUnits = ArrayList<Cluster<MobileUnit>>()
        val myClusters = ArrayList<Cluster<PlayerUnit>>()
        val enemyClusters = ArrayList<Cluster<PlayerUnit>>()

        fun getClusterOf(unit: PlayerUnit) = mobileCombatUnits.firstOrNull { it.units.contains(unit) }

        fun reset() {
            mobileCombatUnits.clear()
            myClusters.clear()
            enemyClusters.clear()
        }

        fun step() {
            updateClusters(mobileCombatUnits, UnitQuery.myUnits.filterIsInstance(MobileUnit::class.java)
                    .filter { it.isCompleted && it !is Worker && it is Attacker })
            updateClusters(myClusters, UnitQuery.myUnits)
            updateClusters(enemyClusters, UnitQuery.enemyUnits + EnemyInfo.seenUnits)
        }

        private fun <U : Unit> updateClusters(clusters: MutableList<Cluster<U>>, relevantUnits: List<U>): List<Cluster<U>> {
            clusters.forEach { it.units.removeIf { !it.exists() }}
            val newUnits = relevantUnits - clusters.flatMap { it.units }
            val clusterForNewUnits = Cluster<U>(Position(0, 0), ArrayList())
            clusters.add(clusterForNewUnits)
            val clusterUnits = newUnits.map { UnitInCluster(clusterForNewUnits, it) } +
                    clusters.flatMap { c -> c.units.map { UnitInCluster(c, it) } }

            clusters.removeIf { it.units.isEmpty() }
            var maxTries = 3
            do {
                clusters.forEach {
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
                        val newCluster = Cluster(unit.position, mutableListOf(unit), unit.position)
                        clusters.add(newCluster)
                        cu.cluster = newCluster
                        true
                    }
                }
                clusters.removeIf { it.units.isEmpty() }
                clusters.forEach {
                    val size = it.units.size
                    it.lastUnitCount = size
                    it.position = it.aggPosition.div(size)
                }
            } while (changes > 0 && maxTries-- > 0)
            return clusters.toList()
        }
    }

    private class UnitInCluster<U : Unit>(var cluster: Cluster<U>, val unit: U)
}