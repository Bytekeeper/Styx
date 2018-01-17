package org.fttbot.info

import org.fttbot.FTTBot
import org.openbw.bwapi4j.unit.*

const val DISCARD_HIDDEN_UNITS_AFTER = 480

object EnemyState {
    val seenUnits = HashSet<PlayerUnit>()
    val enemyBases = ArrayList<Cluster<PlayerUnit>>()
    var hasSeenBase = false
    var hasInvisibleUnits = false


    fun onUnitShow(unit: PlayerUnit) {
        if (unit is Cloakable || unit is Lurker) hasInvisibleUnits = true
        seenUnits.remove(unit)
    }

    fun onUnitHide(unit: PlayerUnit) {
        seenUnits.add(unit)
    }

    fun onUnitDestroy(unit: PlayerUnit) {
        seenUnits.remove(unit)
    }

    fun step() {
        enemyBases.clear()
        enemyBases.addAll(Cluster.enemyClusters.filter { it.units.any { it is Building } })
        hasSeenBase = hasSeenBase || !enemyBases.isEmpty()
        if (!hasSeenBase && FTTBot.bwtaAvailable) {
            val unexploredLocations = FTTBot.bwta.startLocations.filter { !FTTBot.game.bwMap.isExplored(it.tilePosition) }
            if (unexploredLocations.size == 1) {
                enemyBases.add(Cluster(unexploredLocations.first().position, mutableSetOf()))
            }
        }
        seenUnits.removeIf {
            (it !is Burrowable || !it.isBurrowed)
                    && (it !is Cloakable || !it.isCloaked)
                    && (it is MobileUnit)
                    && (FTTBot.frameCount - it.lastSpotted > DISCARD_HIDDEN_UNITS_AFTER
                    || FTTBot.game.bwMap.isVisible(it.tilePosition))
        }
    }
}

