package org.styx.macro

import org.styx.MemoLeaf
import org.styx.NodeStatus
import org.styx.Styx

class Expand() : MemoLeaf() {
    private lateinit var build : Build

    override fun tick(): NodeStatus {
        if (!::build.isInitialized) {
            build = Build(Styx.self.race.resourceDepot) {
                Styx.bases.bases.filter { it.mainResourceDepot == null }
                        .minBy { candidate ->
                            Styx.bases.myBases.map { Styx.map.getPathLength(it.center, candidate.center) }.min()
                                    ?: Int.MAX_VALUE
                        }
                        ?.centerTile
            }
        }
        return build.perform()
    }

}