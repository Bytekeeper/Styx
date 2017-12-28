package org.fttbot

import bwapi.Position
import bwapi.TilePosition
import com.badlogic.gdx.math.ConvexHull
import com.badlogic.gdx.math.Intersector
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.math.Rectangle
import org.fttbot.behavior.RESOURCE_RANGE
import org.fttbot.import.FUnitType
import org.fttbot.layer.FUnit

object ConstructionPosition {
    private val convexHull = ConvexHull()
    var resourcePolygons = HashMap<FUnit, Polygon>()

    fun findPositionFor(unitType: FUnitType): TilePosition? {
        if (unitType.isRefinery) {
            return findPositionForGas(unitType);
        }

        val base = FUnit.myBases()[0]
        val center = base.position.toTilePosition()
        var bestBuildPosition: TilePosition? = null
        var bestDistance: Int = Int.MAX_VALUE
        for (i in -15..15) {
            for (j in -15..15) {
                val dist = i * i + j * j
                val pos = center.translated(i, j);
                if (dist < bestDistance
                        && FTTBot.game.canBuildHere(pos, unitType.source)
                        && outsideOfResourceLines(pos, unitType)
                        && (hasNoAddonOrEnoughSpace(unitType, pos))) {
                    bestDistance = dist
                    bestBuildPosition = pos
                }
            }
        }
        return bestBuildPosition
    }

    private fun hasNoAddonOrEnoughSpace(unitType: FUnitType, pos: TilePosition): Boolean {
        if (!unitType.canBuildAddon) return true
        val addonRect = Rectangle((pos.x + unitType.tileWidth).toFloat(), pos.y.toFloat(), 2f, unitType.tileHeight.toFloat())
        return !FUnit.unitsInRadius(pos.toPosition() + Position(unitType.width, unitType.height), 100)
                .any { it.isBuilding && addonRect.overlaps(Rectangle(it.tilePosition.x.toFloat(), it.tilePosition.y.toFloat(), it.type.tileWidth.toFloat(), it.type.tileHeight.toFloat())) }
    }

    private fun outsideOfResourceLines(pos: TilePosition, unitType: FUnitType): Boolean {
        val base = FUnit.myBases().minBy { it.distanceInTilesTo(pos) } ?: return true
        val poly = resourcePolygons.computeIfAbsent(base) {
            val relevantUnits = FUnit.unitsInRadius(base.position, RESOURCE_RANGE)
                    .filter { it.isMineralField || it.type == FUnitType.Resource_Vespene_Geyser }
                    .toMutableList()
            relevantUnits.add(base)
            val points = FloatArray(relevantUnits.size * 8)
            relevantUnits.forEachIndexed() { index, fUnit ->
                val base = index * 8
                points[base] = fUnit.tilePosition.x.toFloat()
                points[base + 1] = fUnit.tilePosition.y.toFloat()
                points[base + 2] = fUnit.tilePosition.x.toFloat() + fUnit.type.tileWidth
                points[base + 3] = fUnit.tilePosition.y.toFloat()
                points[base + 4] = fUnit.tilePosition.x.toFloat() + fUnit.type.tileWidth
                points[base + 5] = fUnit.tilePosition.y.toFloat() + fUnit.type.tileHeight
                points[base + 6] = fUnit.tilePosition.x.toFloat()
                points[base + 7] = fUnit.tilePosition.y.toFloat() + fUnit.type.tileHeight
            }
            return@computeIfAbsent Polygon(convexHull.computePolygon(points, false).toArray())
        }
        if (poly.contains(pos.toVector())) return false
        if (poly.contains(pos.toVector().add(unitType.tileWidth.toFloat(), 0f))) return false
        if (poly.contains(pos.toVector().add(unitType.tileWidth.toFloat(), unitType.tileHeight.toFloat()))) return false
        if (poly.contains(pos.toVector().add(0f, unitType.tileHeight.toFloat()))) return false
        return true
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