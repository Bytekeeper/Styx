package org.styx

import bwapi.Position
import bwapi.TilePosition
import bwapi.UnitType
import bwapi.Unit
import com.badlogic.gdx.math.ConvexHull
import com.badlogic.gdx.math.Polygon
import com.badlogic.gdx.math.Rectangle
import org.styx.Context.find
import org.styx.Context.game
import org.styx.Context.geography

object ConstructionPosition {
    val convexHull = ConvexHull()
    var resourcePolygons = HashMap<Unit, Polygon>()

    fun findPositionFor(unitType: UnitType, builder: Unit?, near: Position? = null): TilePosition? {
        if (unitType.isRefinery) {
            return findPositionForGas();
        }

        val target = if (near != null) near.toTilePosition() else
            if (!geography.myBases.isEmpty()) (geography.myBases.maxBy { find.myWorkers.inRadius(it.x, it.y, 300).size } )!!.tilePosition
            else return null
        var bestBuildPosition: TilePosition? = null
        var bestDistance: Int = Int.MAX_VALUE
        for (i in -15..15) {
            for (j in -15..15) {
                val dist = i * i + j * j
                val pos = target.translated(i, j);
                if (!pos.isValid(game))
                    continue
                // Simple way to leave gaps
                if ((i != 0 || j != 0) && (pos.x % 3 < 1 || pos.y % 5 < 2))
                    continue
                if (dist < bestDistance
                        && game.canBuildHere(pos, unitType, builder)
                        && outsideOfResourceLines(pos, unitType)
                        && hasNoAddonOrEnoughSpace(unitType, pos)
                        && willNotBlockOtherAddon(unitType, pos)) {
                    if (find.groundUnits.inRadius(pos.toPosition().translated(unitType.width() / 2, unitType.height() / 2), 32)
                            .any { !it.type.isWorker && it.type != UnitType.Resource_Vespene_Geyser }) {
                        println("MEH")
                    }
                    bestDistance = dist
                    bestBuildPosition = pos
                }
            }
        }
        return bestBuildPosition
    }

    private fun willNotBlockOtherAddon(unitType: UnitType, pos: TilePosition): Boolean {
        val building = Rectangle(pos.x.toFloat(), pos.y.toFloat(), unitType.tileWidth().toFloat(), unitType.tileHeight().toFloat())
        val pixelPos = pos.toPosition()
        return !find.groundUnits.inRadius(pixelPos.x, pixelPos.y + unitType.height(), 100)
                .any {
                    it.type.canBuildAddon() && building.overlaps(
                            Rectangle(it.tilePosition.x.toFloat() + it.type.tileWidth().toFloat(),
                                    it.tilePosition.y.toFloat(), 2f, it.type.tileHeight().toFloat()))
                }
    }

    private fun hasNoAddonOrEnoughSpace(unitType: UnitType, pos: TilePosition): Boolean {
        if (!unitType.canBuildAddon()) return true
        val addonRect = Rectangle((pos.x + unitType.tileWidth()).toFloat(), pos.y.toFloat(), 2f, unitType.tileHeight().toFloat())
        val pixelPos = pos.toPosition()

        return game.canBuildHere(TilePosition(pos.x + unitType.tileWidth(), pos.y + 1), unitType)
                && !find.groundUnits.inRadius(pixelPos.x + unitType.width(), pixelPos.y + unitType.height(), 100)
                .any { it.type.isBuilding && addonRect.overlaps(Rectangle(it.tilePosition.x.toFloat(), it.tilePosition.y.toFloat(), it.type.tileWidth().toFloat(), it.type.tileHeight().toFloat())) }
    }

    private fun outsideOfResourceLines(pos: TilePosition, unitType: UnitType): Boolean {
        val base = find.myResourceDepots.minBy { it.tilePosition.getDistance(pos) }
                ?: return true
        val poly = resourcePolygons.computeIfAbsent(base) {
            val relevantUnits = find.groundUnits.inRadius(base.position, 300)
                    .filter { it.type.isMineralField || it.type.isRefinery || it.type == UnitType.Resource_Vespene_Geyser }
                    .toMutableList()
            relevantUnits.add(base)
            val points = FloatArray(relevantUnits.size * 8)
            relevantUnits.forEachIndexed() { index, unit ->
                val idx = index * 8
                val type = unit.type
                points[idx] = unit.tilePosition.x.toFloat()
                points[idx + 1] = unit.tilePosition.y.toFloat()
                points[idx + 2] = unit.tilePosition.x.toFloat() + type.tileWidth()
                points[idx + 3] = unit.tilePosition.y.toFloat()
                points[idx + 4] = unit.tilePosition.x.toFloat() + type.tileWidth()
                points[idx + 5] = unit.tilePosition.y.toFloat() + type.tileHeight()
                points[idx + 6] = unit.tilePosition.x.toFloat()
                points[idx + 7] = unit.tilePosition.y.toFloat() + type.tileHeight()
            }
            return@computeIfAbsent Polygon(convexHull.computePolygon(points, false).toArray())
        }
        if (poly.contains(pos.toVector())) return false
        if (poly.contains(pos.toVector().add(unitType.tileWidth().toFloat(), 0f))) return false
        if (poly.contains(pos.toVector().add(unitType.tileWidth().toFloat(), unitType.tileHeight().toFloat()))) return false
        if (poly.contains(pos.toVector().add(0f, unitType.tileHeight().toFloat()))) return false
        return true
    }

    private fun findPositionForGas(): TilePosition? {
        val geysers = find.geysers
        return find.myResourceDepots.mapNotNull { base ->
            val geyser = geysers.filter { it.getDistance(base) < 300 }.minBy { it.getDistance(base) }
            return@mapNotNull if (geyser != null) {
                base to geyser.initialTilePosition
            } else null
        }.minBy {
            if (it.first.isCompletedTrainer) -1
            else it.first.remainingBuildTime
        }?.second
    }
}