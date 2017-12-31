package org.fttbot

import com.badlogic.gdx.math.ConvexHull
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.math.Rectangle
import org.fttbot.behavior.RESOURCE_RANGE
import org.fttbot.layer.UnitQuery
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit

object ConstructionPosition {
    private val convexHull = ConvexHull()
    var resourcePolygons = HashMap<Unit, Polygon>()

    fun findPositionFor(unitType: UnitType): TilePosition? {
        if (unitType.isRefinery) {
            return findPositionForGas(unitType);
        }

        val base = UnitQuery.myBases[0]
        val center = base.position.toTilePosition()
        var bestBuildPosition: TilePosition? = null
        var bestDistance: Int = Int.MAX_VALUE
        for (i in -15..15) {
            for (j in -15..15) {
                val dist = i * i + j * j
                val pos = center.translated(i, j);
                if (dist < bestDistance
                        && FTTBot.game.canBuildHere(pos, unitType)
                        && outsideOfResourceLines(pos, unitType)
                        && hasNoAddonOrEnoughSpace(unitType, pos)
                        && willNotBlockOtherAddon(unitType, pos)) {
                    bestDistance = dist
                    bestBuildPosition = pos
                }
            }
        }
        return bestBuildPosition
    }

    private fun willNotBlockOtherAddon(unitType: UnitType, pos: TilePosition): Boolean {
        val building = Rectangle(pos.x.toFloat(), pos.y.toFloat(), unitType.tileWidth().toFloat(), unitType.tileHeight().toFloat())
        return !UnitQuery.unitsInRadius(pos.toPosition() + Position(0, unitType.height()), 100)
                .any { it is ExtendibleByAddon && building.overlaps(
                        Rectangle(it.tilePosition.x.toFloat() + it.tileWidth().toFloat(), it.tilePosition.y.toFloat(), 2f, it.tileHeight().toFloat())) }
    }

    private fun hasNoAddonOrEnoughSpace(unitType: UnitType, pos: TilePosition): Boolean {
        if (!unitType.canBuildAddon()) return true
        val addonRect = Rectangle((pos.x + unitType.tileWidth()).toFloat(), pos.y.toFloat(), 2f, unitType.tileHeight().toFloat())
        return !UnitQuery.unitsInRadius(pos.toPosition() + Position(unitType.width(), unitType.height()), 100)
                .any { it is Building && addonRect.overlaps(Rectangle(it.tilePosition.x.toFloat(), it.tilePosition.y.toFloat(), it.tileWidth().toFloat(), it.tileHeight().toFloat())) }
    }

    private fun outsideOfResourceLines(pos: TilePosition, unitType: UnitType): Boolean {
        val base = UnitQuery.myBases.minBy { it.tilePosition.getDistance(pos) } ?: return true
        val poly = resourcePolygons.computeIfAbsent(base) {
            val relevantUnits = UnitQuery.unitsInRadius(base.position, RESOURCE_RANGE)
                    .filter { it is MineralPatch || it is VespeneGeyser }
                    .toMutableList()
            relevantUnits.add(base)
            val points = FloatArray(relevantUnits.size * 8)
            relevantUnits.forEachIndexed() { index, unit ->
                val idx = index * 8
                points[idx] = unit.tilePosition.x.toFloat()
                points[idx + 1] = unit.tilePosition.y.toFloat()
                points[idx + 2] = unit.tilePosition.x.toFloat() + unit.tileWidth()
                points[idx + 3] = unit.tilePosition.y.toFloat()
                points[idx + 4] = unit.tilePosition.x.toFloat() + unit.tileWidth()
                points[idx + 5] = unit.tilePosition.y.toFloat() + unit.tileHeight()
                points[idx + 6] = unit.tilePosition.x.toFloat()
                points[idx + 7] = unit.tilePosition.y.toFloat() + unit.tileHeight()
            }
            return@computeIfAbsent Polygon(convexHull.computePolygon(points, false).toArray())
        }
        if (poly.contains(pos.toVector())) return false
        if (poly.contains(pos.toVector().add(unitType.tileWidth().toFloat(), 0f))) return false
        if (poly.contains(pos.toVector().add(unitType.tileWidth().toFloat(), unitType.tileHeight().toFloat()))) return false
        if (poly.contains(pos.toVector().add(0f, unitType.tileHeight().toFloat()))) return false
        return true
    }

    private fun findPositionForGas(unitType: UnitType): TilePosition? {
        val geysers = UnitQuery.geysers
        UnitQuery.myBases.forEach { base ->
            val geyser = geysers.filter { it.getDistance(base) < 300 }.minBy { it.getDistance(base) }
            if (geyser != null) {
                return geyser.initialTilePosition
            }
        }
        return null
    }
}