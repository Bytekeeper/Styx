package org.fttbot

import bwapi.TilePosition
import com.badlogic.gdx.math.ConvexHull
import com.badlogic.gdx.math.Polygon
import org.fttbot.behavior.RESOURCE_RANGE
import org.fttbot.layer.FUnit
import org.fttbot.layer.FUnitType

object ConstructionPosition {
    private val convexHull = ConvexHull()
    private var resourcePolygons = HashMap<FUnit, Polygon>()

    fun findPositionFor(unitType: FUnitType): TilePosition? {
        if (unitType.isRefinery) {
            return findPositionForGas(unitType);
        }

        val base = FUnit.myBases()[0]
        val center = base.position.toTilePosition()
        var bestBuildPosition: TilePosition? = null
        var bestDistance: Int = Int.MAX_VALUE
        for (i in -10..10) {
            for (j in -10..10) {
                val dist = i * i + j * j
                val pos = center.translated(i, j);
                if (dist < bestDistance
                        && FTTBot.game.canBuildHere(pos, unitType.type)
                        && outsideOfResourceLines(pos)) {
                    bestDistance = dist
                    bestBuildPosition = pos
                }
            }
        }
        return bestBuildPosition
    }

    private fun outsideOfResourceLines(pos: TilePosition): Boolean {
        val base = FUnit.myBases().minBy { it.distanceTo(pos) } ?: return true
        val poly = resourcePolygons.computeIfAbsent(base) {
            val relevantUnits = FUnit.unitsInRadius(base.position, RESOURCE_RANGE)
                    .filter { it.isMineralField || it.type == FUnitType.Resource_Vespene_Geyser }
                    .toMutableList()
            relevantUnits.add(base)
            val points = FloatArray(relevantUnits.size * 2)
            relevantUnits.forEachIndexed() { index, fUnit ->
                points[index * 2] = fUnit.tilePosition.x.toFloat()
                points[index * 2 + 1] = fUnit.tilePosition.y.toFloat()
            }
            return@computeIfAbsent Polygon(convexHull.computePolygon(points, false).toArray())
        }
        return !poly.contains(pos.toVector())
    }

    private fun findPositionForGas(unitType: FUnitType): TilePosition? {
        val geysers = FUnit.neutrals().filter { it.type == FUnitType.Resource_Vespene_Geyser }
        FUnit.myBases().forEach { base ->
            val geyser = base.closest(geysers.filter { it.distanceTo(base) < 300 })
            if (geyser != null) {
                return geyser.initialTilePosition
            }
        }
        return null
    }
}