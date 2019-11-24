package org.styx

import bwapi.Color
import bwapi.Position
import bwapi.TilePosition
import bwapi.UnitType
import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.geom.*
import org.styx.Styx.bases
import org.styx.Styx.game
import org.styx.Styx.units


object ConstructionPosition {
    private val resourceBlockedGeometry = mutableMapOf<Base, Polygonal>()
    private val geometryFactory = GeometryFactory()

    fun findPositionFor(unitType: UnitType, near: Position? = null): TilePosition? {
        if (unitType.isRefinery) {
            return findPositionForGas();
        }

        val target = near?.toTilePosition() ?: bases.myBases.mapNotNull { it.mainResourceDepot }
                .maxBy { Styx.units.myWorkers.inRadius(it.x, it.y, 300).size }?.tilePosition ?: return null
        var bestBuildPosition: TilePosition? = null
        var bestDistance: Int = Int.MAX_VALUE
        for (i in -15..15) {
            for (j in -15..15) {
                val dist = i * i + j * j
                val pos = target + TilePosition(i, j)
                if (!pos.isValid(Styx.game))
                    continue
                // Simple way to leave gaps
                if ((i != 0 || j != 0) && (pos.x % 3 < 1 || pos.y % 5 < 2))
                    continue
                if (dist < bestDistance
                        && game.canBuildHere(pos, unitType)
                        && outsideOfResourceLines(pos, unitType)
                        && hasNoAddonOrEnoughSpace(unitType, pos)
                        && willNotBlockOtherAddon(unitType, pos)
                ) {
                    bestDistance = dist
                    bestBuildPosition = pos
                }
            }
        }
        return bestBuildPosition
    }

    private fun willNotBlockOtherAddon(unitType: UnitType, pos: TilePosition): Boolean {
        val building = Envelope(pos.x.toDouble(), pos.y.toDouble(), unitType.tileWidth().toDouble(), unitType.tileHeight().toDouble())
        return !units.mine.inRadius(pos.toPosition() + Position(0, unitType.height()), 100)
                .any {
                    val type = it.unitType
                    type.canBuildAddon() && building.intersects(
                            Envelope(it.tilePosition.x.toDouble() + type.tileWidth().toDouble(),
                                    it.tilePosition.y.toDouble(), 2.0, type.tileHeight().toDouble()))
                }
    }

    private fun hasNoAddonOrEnoughSpace(unitType: UnitType, pos: TilePosition): Boolean {
        if (!unitType.canBuildAddon()) return true
        val addonRect = Envelope((pos.x + unitType.tileWidth()).toDouble(), pos.y.toDouble(), 2.0, unitType.tileHeight().toDouble())

        return game.canBuildHere(TilePosition(pos.x + unitType.tileWidth(), pos.y + 1), unitType)
                && !units.allunits.inRadius(pos.toPosition() + Position(unitType.width(), unitType.height()), 100)
                .any {
                    it.unitType.isBuilding &&
                            addonRect.intersects(Envelope(it.tilePosition.x.toDouble(),
                                    it.tilePosition.y.toDouble(),
                                    it.unitType.tileWidth().toDouble(),
                                    it.unitType.tileHeight().toDouble()))
                }
    }

    private fun outsideOfResourceLines(pos: TilePosition, unitType: UnitType): Boolean {
        val base = bases.myBases.filter { it.mainResourceDepot != null }.minBy { it.centerTile.getDistance(pos) }
                ?: return true
        val poly = resourceBlockedGeometry.computeIfAbsent(base) {
            val relevantUnits = units.allunits.inRadius(base.center, 400)
                    .filter { it.unitType.isResourceContainer || it.unitType.isRefinery }
                    .toMutableList()
            val blockedGeometry = relevantUnits.map {
                ConvexHull(geometryFactory.createGeometryCollection(arrayOf(it.tileGeometry, base.mainResourceDepot!!.tileGeometry)))
                        .convexHull
            }.toTypedArray()
            return@computeIfAbsent geometryFactory.createGeometryCollection(blockedGeometry).union() as Polygonal
        }
        val endpos = pos + unitType.tileSize()
        val toTest = geometryFactory.toGeometry(Envelope(Coordinate(pos.x.toDouble(), pos.y.toDouble()), Coordinate(endpos.x.toDouble(), endpos.y.toDouble())))
        return poly is Geometry && !poly.intersects(toTest)
    }

    private fun findPositionForGas(): TilePosition? {
        val geysers = Styx.units.geysers
        return bases.myBases.asSequence()
                .mapNotNull { base ->
                    val candidate = geysers.nearest(base.center.x, base.center.y)?.tilePosition
                            ?: return@mapNotNull null
                    if (candidate.getDistance(base.centerTile) < 8) candidate else null
                }.firstOrNull()
    }
}