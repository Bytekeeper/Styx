package org.styx.macro

import org.bk.ass.bt.BehaviorTree
import org.bk.ass.bt.Memo
import org.bk.ass.bt.TreeNode
import org.styx.Styx
import org.styx.Styx.game
import org.styx.Styx.self

class Expand(private val requireGas: Boolean = true) : BehaviorTree() {

    override fun getRoot(): TreeNode =
            Memo(
                    Build(
                            BuildBoard(
                                    self.race.resourceDepot,
                                    this::findExpandLocation)
                    )
            )

    private fun findExpandLocation(board: BuildBoard) {
        if (board.location == null && board.workerLock.item == null) {
            board.location = Styx.bases.bases.filter {
                it.mainResourceDepot == null &&
                        (!requireGas || it.hasGas) &&
                        game.canBuildHere(it.centerTile, self.race.resourceDepot)
            }.minBy { candidate ->
                Styx.bases.myBases.map { Styx.map.getPathLength(it.center, candidate.center) }.sum() -
                        3 * (Styx.units.enemy.nearest(candidate.center.x, candidate.center.y) { it.unitType.isBuilding }?.distanceTo(candidate.center)
                        ?: Styx.bases.potentialEnemyBases.map { it.center.getApproxDistance(candidate.center) }.min()
                        ?: 0)

            }?.centerTile
        }
    }
}