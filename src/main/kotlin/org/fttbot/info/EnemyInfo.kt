package org.fttbot.info

import bwem.area.Area
import org.fttbot.FTTBot
import org.fttbot.LazyOnFrame
import org.openbw.bwapi4j.Player
import org.openbw.bwapi4j.UnitStatCalculator
import org.openbw.bwapi4j.unit.*

const val DISCARD_HIDDEN_UNITS_AFTER = 480

object EnemyInfo {
    private val statsCalc = HashMap<Player, UnitStatCalculator>()
    val seenUnits = ArrayList<PlayerUnit>()
    val enemyBases = ArrayList<Cluster<PlayerUnit>>()
    var hasSeenBase = false
    var hasInvisibleUnits = false
    val occupiedAreas by LazyOnFrame<Map<Area, List<PlayerUnit>>> {
        (UnitQuery.enemyUnits + seenUnits).filter { it is Attacker }
                .groupBy { FTTBot.bwem.getArea(it.tilePosition) }
    }

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

    fun onUnitRenegade(unit: PlayerUnit) {
        if (!unit.isEnemyUnit) {
            seenUnits.remove(unit)
        }
    }

    fun reset() {
        seenUnits.clear()
        enemyBases.clear()
        hasSeenBase = false
        hasInvisibleUnits = false
    }

    fun step() {
        enemyBases.clear()
        enemyBases.addAll(Cluster.enemyClusters.filter { it.units.any { it is Building } })
        hasSeenBase = hasSeenBase || !enemyBases.isEmpty()
        if (!hasSeenBase) {
            val unexploredLocations = FTTBot.game.bwMap.startPositions.filter { !FTTBot.game.bwMap.isExplored(it) }
            if (unexploredLocations.size == 1) {
                enemyBases.add(Cluster(unexploredLocations.first().toPosition(), ArrayList()))
            }
        }
        seenUnits.removeIf {
            it is MobileUnit &&
                    ((FTTBot.frameCount - it.lastSpotted > DISCARD_HIDDEN_UNITS_AFTER * 2) ||
                    (FTTBot.frameCount - it.lastSpotted > DISCARD_HIDDEN_UNITS_AFTER ) && (it !is SiegeTank || !it.isSieged))
                    FTTBot.game.bwMap.isVisible(it.tilePosition)
        }
    }
}

