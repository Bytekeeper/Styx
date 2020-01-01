package org.styx.global

import bwapi.Position
import bwapi.TilePosition
import org.bk.ass.bt.TreeNode
import org.styx.SUnit
import org.styx.Styx
import org.styx.nearest

class Base(val centerTile: TilePosition,
           val center: Position,
           val isStartingLocation: Boolean,
           var mainResourceDepot: SUnit? = null,
           var lastSeenFrame: Int? = null,
           var hasGas: Boolean,
           var populated: Boolean = false)

class Bases : TreeNode() {
    lateinit var bases: List<Base>
        private set
    lateinit var myBases: List<Base>
        private set
    lateinit var enemyBases: List<Base>
        private set
    lateinit var potentialEnemyBases: List<Base>
        private set

    override fun init() {
        super.init()
        bases = Styx.map.bases.map {
            Base(it.location, it.center, it.isStartingLocation, hasGas = it.geysers.isNotEmpty())
        }
        success()
    }

    override fun exec() {
        bases.forEach {
            val resourceDepot = Styx.units.resourceDepots.nearest(it.center.x, it.center.y)
            if (resourceDepot != null && resourceDepot.distanceTo(it.center) < 80)
                it.mainResourceDepot = resourceDepot
            if (Styx.game.isVisible(it.centerTile)) {
                it.lastSeenFrame = Styx.frame
                it.populated = Styx.units.ownedUnits.nearest(it.center.x, it.center.y) { it.unitType.isBuilding }?.distanceTo(it.center) ?: Int.MAX_VALUE < 400
            }
        }
        myBases = bases.filter { it.mainResourceDepot?.myUnit == true }
        enemyBases = (bases - myBases).filter { it.populated }
        potentialEnemyBases = bases.filter {
            it.isStartingLocation &&
                    !it.populated &&
                    !Styx.game.isExplored(it.centerTile)
        }.sortedBy {base ->
            Styx.units.enemy.map { it.firstSeenPosition.getApproxDistance(base.center) }.min() ?: Int.MAX_VALUE
        }
    }
}