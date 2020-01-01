package org.styx.global

import org.bk.ass.bt.TreeNode
import org.bk.ass.cluster.Cluster
import org.bk.ass.cluster.StableDBScanner
import org.styx.SUnit
import org.styx.Styx
import org.styx.squad.SquadBoard

class Squads : TreeNode() {
    private val dbScanner = StableDBScanner<SUnit>(2)

    private var clusters: Collection<Cluster<SUnit>> = emptyList()
        private set
    private val _squads = mutableMapOf<Cluster<SUnit>, SquadBoard>()
    val squads = _squads.values

    override fun exec() {
        success()
        clusters = dbScanner.updateDB(Styx.units.ownedUnits, 400)
                .scan(-1)
                .clusters
        _squads.keys.retainAll(clusters)

        Styx.squads.clusters.forEach { cluster ->
            val units = cluster.elements.toMutableList()
            _squads.computeIfAbsent(cluster) { SquadBoard() }
                    .update(units)
        }
    }
}