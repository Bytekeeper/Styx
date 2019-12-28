package org.styx.global

import org.styx.Styx
import org.styx.nearest

class Bases {
    lateinit var bases: List<Base>
        private set
    lateinit var myBases: List<Base>
        private set
    lateinit var enemyBases: List<Base>
        private set
    lateinit var potentialEnemyBases: List<Base>
        private set

    fun update() {
        if (!this::bases.isInitialized) {
            bases = Styx.map.bases.map {
                Base(it.location, it.center, it.isStartingLocation, hasGas = it.geysers.isNotEmpty())
            }
        }
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
        }.sortedBy {
            Styx.units.enemy.nearest(it.center)?.framesToTravelTo(it.center) ?: Int.MAX_VALUE
        }
    }
}