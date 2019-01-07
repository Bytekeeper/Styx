package org.styx

import bwapi.Color
import bwapi.TilePosition
import bwapi.Unit
import org.styx.Context.find
import org.styx.Context.frameCount
import org.styx.Context.game
import org.styx.Context.map
import org.styx.Context.self
import org.styx.Context.visualization

class Reconnaissance {
    val hiddenEnemies = mutableSetOf<UnitInfo>()
    val potentialBaseLocations = mutableSetOf<TilePosition>()

    fun enemyBases(): List<TilePosition> {
        val candidates = map.bases.filter { base ->
            find.enemyUnits.inRadius(base.center, 400).any { it.type.isBuilding }
                    || hiddenEnemies.any { it.type.isBuilding && it.lastKnownPosition.getDistance(base.center) < 400 }
        }.map { it.location }
        if (candidates.isNotEmpty()) return candidates
        if (potentialBaseLocations.isNotEmpty()) return potentialBaseLocations.toList()
        val unexplored = game.startLocations.filter { !game.isExplored(it) }
        if (unexplored.size == 1) return unexplored
        return emptyList()
    }


    fun reset() {
        hiddenEnemies.clear()
    }

    fun update() {
        val visibleEnemies = find.enemyUnits.map { it.id }.toSet()
        hiddenEnemies.removeIf { visibleEnemies.contains(it.id) || game.isVisible(it.lastKnownPosition.toTilePosition()) }

        if (potentialBaseLocations.isEmpty() && find.enemyUnits.isNotEmpty()) {
            val candidates =
                    if (frameCount < (90 + game.mapWidth() * game.mapHeight() / 200) * 24)
                        game.startLocations
                    else
                        map.bases.map { it.location }
            potentialBaseLocations += candidates.filter { base ->
                find.enemyUnits.any { enemy ->
                    enemy.framesTo(base.toPosition()) < 20 * 24
                }
            }
        }
        potentialBaseLocations.removeIf { game.isVisible(it) }
        if (visualization.drawSuspectedEnemyBases) {
            potentialBaseLocations.forEach {
                game.drawCircleMap(it.toPosition(), 50, Color.Yellow)
            }
        }
    }

    fun onUnitHide(unit: Unit) {
        if (self.isEnemy(unit.player))
            hiddenEnemies += UnitInfo(unit)
    }

    fun onUnitDestroy(unit: Unit) {
        hiddenEnemies -= UnitInfo(unit)
    }
}
