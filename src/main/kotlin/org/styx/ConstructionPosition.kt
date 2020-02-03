package org.styx

import bwapi.Position
import bwapi.TilePosition
import bwapi.UnitType
import org.locationtech.jts.algorithm.ConvexHull
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.styx.Styx.bases
import org.styx.Styx.game
import org.styx.Styx.map
import org.styx.Styx.units
import org.styx.global.Base


object ConstructionPosition {
    private val resourceBlockedGeometry = mutableMapOf<Base, Geometry>()
    private val geometryFactory = GeometryFactory()

    fun findPositionFor(unitType: UnitType, near: Position? = null): TilePosition? {
        if (unitType.isRefinery) {
            return findPositionForGas();
        }
        val defensiveBuilding = unitType == UnitType.Zerg_Creep_Colony || unitType.canAttack() || unitType == UnitType.Terran_Bunker

        val target = near?.toTilePosition()
                ?: (if (defensiveBuilding) bestPositionForStaticDefense() else null)
                ?: bases.myBases.mapNotNull { it.mainResourceDepot }
                        .maxBy { units.myWorkers.inRadius(it.x, it.y, 300).size }?.tilePosition ?: return null
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
                        && TileReservation.isAvailable(pos)
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
        val blockedArea = resourceBlockedGeometry.computeIfAbsent(base) {
            val relevantUnits = units.allunits.inRadius(base.center, 400)
                    .filter { it.unitType.isResourceContainer || it.unitType.isRefinery }
                    .toMutableList()
            val blockedGeometry = relevantUnits.map {
                ConvexHull(geometryFactory.createGeometryCollection(arrayOf(it.tileGeometry, base.mainResourceDepot!!.tileGeometry)))
                        .convexHull
            }.toTypedArray()
            return@computeIfAbsent geometryFactory.createGeometryCollection(blockedGeometry).union() as Geometry
        }
        val endpos = pos + unitType.tileSize()
        val toTest = geometryFactory.toGeometry(Envelope(Coordinate(pos.x.toDouble(), pos.y.toDouble()), Coordinate(endpos.x.toDouble(), endpos.y.toDouble())))
        return !blockedArea.intersects(toTest)
    }

    private fun findPositionForGas(): TilePosition? {
        val geysers = units.geysers
        return bases.myBases
                .mapNotNull {
                    val geyser = geysers.nearest(it.center.x, it.center.y, 8 * 32) { TileReservation.isAvailable(it.tilePosition) }
                    if (geyser != null)
                        it.mainResourceDepot to geyser
                    else
                        null
                }.minBy { (base, geyser) ->
                    (if (base?.isCompleted != true) 24 else 0) +
                            (base?.remainingBuildTime ?: 0)
                }?.second?.tilePosition
    }

    private fun bestPositionForStaticDefense(): TilePosition? {
        val baseCandidate = bases.myBases.map { base ->
            base to map.getArea(base.centerTile).accessibleNeighbors.filter { a -> bases.myBases.none { base -> map.getArea(base.centerTile) == a } }
        }.filter { it.second.isNotEmpty() }
                .minBy { it.second.size } ?: return null
        val base = baseCandidate.first
        val baseArea = map.getArea(base.centerTile)
        val chokePosition = baseArea.chokePointsByArea[baseCandidate.second.first()]
                ?.firstOrNull()?.center?.toTilePosition() ?: return null
        return (base.centerTile + chokePosition) / 2
    }

}