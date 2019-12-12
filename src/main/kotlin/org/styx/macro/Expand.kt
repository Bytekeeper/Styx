package org.styx.macro

import org.styx.MemoLeaf
import org.styx.NodeStatus
import org.styx.Styx
import org.styx.Styx.game
import org.styx.Styx.self

class Expand(private val requireGas: Boolean = true) : MemoLeaf() {
    private lateinit var build : Build

    override fun tick(): NodeStatus {
        if (!::build.isInitialized) {
            build = Build(self.race.resourceDepot) {
                Styx.bases.bases.filter {
                    it.mainResourceDepot == null &&
                            (!requireGas || it.hasGas) &&
                            game.canBuildHere(it.centerTile, self.race.resourceDepot)
                }.minBy { candidate ->
                            Styx.bases.myBases.map { Styx.map.getPathLength(it.center, candidate.center) }.min()
                                    ?: Int.MAX_VALUE
                        }
                        ?.centerTile
            }
        }
        return build()
    }

}