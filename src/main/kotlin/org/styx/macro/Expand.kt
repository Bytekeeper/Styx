package org.styx.macro

import org.styx.*
import org.styx.Styx.game
import org.styx.Styx.self

class Expand(private val requireGas: Boolean = true) : BehaviorTree("Expand") {

    override fun buildRoot(): SimpleNode = Memo(
            Build(
                    BuildBoard(
                            self.race.resourceDepot,
                            this::findExpandLocation)
            )
    )

    private fun findExpandLocation(board: BuildBoard) {
        if (board.location == null && board.workerLock.unit == null) {
            board.location = Styx.bases.bases.filter {
                it.mainResourceDepot == null &&
                        (!requireGas || it.hasGas) &&
                        game.canBuildHere(it.centerTile, self.race.resourceDepot)
            }.minBy { candidate ->
                Styx.bases.myBases.map { Styx.map.getPathLength(it.center, candidate.center) }.sum() -
                        3 * (Styx.units.enemy.nearest(candidate.center.x, candidate.center.y) { it.unitType.isBuilding }?.distanceTo(candidate.center)
                        ?: 0)

            }?.centerTile
        }
    }
}