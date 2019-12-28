package org.styx.global

import org.bk.ass.cluster.Cluster
import org.bk.ass.cluster.StableDBScanner
import org.styx.SUnit
import org.styx.Styx
import org.styx.squad.Squad

class Squads {
    private val dbScanner = StableDBScanner<SUnit>(2)

    private var clusters: Collection<Cluster<SUnit>> = emptyList()
        private set
    private val _squads = mutableMapOf<Cluster<SUnit>, Squad>()
    val squads = _squads.values

    fun update() {
        clusters = dbScanner.updateDB(Styx.units.ownedUnits, 400)
                .scan(-1)
                .clusters
        _squads.keys.retainAll(clusters)

        Styx.squads.clusters.forEach { cluster ->
            val units = cluster.elements.toMutableList()
            _squads.computeIfAbsent(cluster) { Squad() }
                    .update(units)
        }
    }
}