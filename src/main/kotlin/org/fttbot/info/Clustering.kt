package org.fttbot.info

import org.fttbot.div
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Unit
import org.openbw.bwapi4j.unit.Worker

object Cluster {
    var mobileCombatUnits: List<Cluster> = emptyList()

    fun step() {
        mobileCombatUnits = buildClusters(UnitQuery.myUnits.filter { it.isCompleted && it is MobileUnit && it !is Worker<*> })
    }

    private fun buildClusters(relevantUnits: List<Unit>): List<Cluster> {
        val clusters: MutableList<Cluster> = ArrayList()
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
                val cluster = clusters.filter { it.position.getDistance(unit.position) < 300 }.maxBy { it.lastUnitCount }
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
        } while (changes > 0)
        return clusters.toList()
    }

    class Cluster(var position: Position, internal val units: MutableSet<Unit>, internal var aggPosition: Position, internal var lastUnitCount : Int = 0)
    private class UnitInCluster<T : Unit>(var cluster: Cluster, val unit: T)
}