package org.fttbot.info

import bwem.ChokePointImpl
import bwem.area.typedef.AreaId
import bwem.typedef.Index
import org.fttbot.FTTBot
import org.fttbot.isValidPosition
import org.fttbot.toCoordinate
import org.locationtech.jts.geom.CoordinateList
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.WalkPosition

object MapQuery {
    private val HALF_WALK = Position(4, 4)
    val areaPolys = mutableMapOf<AreaId, Polygon>()
    val chokePolys = mutableMapOf<Index, Polygon>()
    private val geometryFactory = GeometryFactory()


    fun reset() {
        if (FTTBot.bwemInitializer.isDone && chokePolys.isEmpty()) {
            for (chokePoint in FTTBot.bwem.chokePoints) {
                val poly = geometryFactory.createMultiPointFromCoords(chokePoint.geometry.map { it.toPosition().add(HALF_WALK).toCoordinate() }.toTypedArray())
                        .convexHull() as? Polygon
                if (poly != null) {
                    chokePolys[(chokePoint as ChokePointImpl).index] = poly
                }
            }
//            for (area in FTTBot.bwem.areas) {
//                val tl = area.topLeft.toWalkPosition()
//                val br = area.bottomRight.toWalkPosition()
//                for (y in tl.y..br.y) {
//                    for (x in tl.x..br.x) {
//                        val wp = WalkPosition(x, y)
//                        if (wp.isValidPosition && FTTBot.bwem.data.getMiniTile(wp).areaId == area.id) {
//                            val multiPoint = geometryFactory.createMultiPointFromCoords(squareTrace(wp, area.id).map { it.toPosition().add(HALF_WALK).toCoordinate() }.toTypedArray())
//                            if (multiPoint.coordinates.size > 4) {
//                                val list = CoordinateList(multiPoint.coordinates)
//                                list.closeRing()
//                                multiPoint.userData = area
//                                var polygon = geometryFactory.createPolygon(list.toCoordinateArray())
//                                if (!polygon.isValid) {
//                                    val buffered = polygon.buffer(0.0)
//                                    if (buffered is Polygon)
//                                        polygon = buffered
//                                    else {
//                                        polygon = (0 until buffered.numGeometries)
//                                                .map { buffered.getGeometryN(it) }
//                                                .maxBy { it.numGeometries } as Polygon
//                                    }
//                                }
//                                areaPolys[area.id] = polygon
//                                continue
//                            }
//                        }
//                    }
//                }
//            }
        }
    }

    private fun squareTrace(start: WalkPosition, id: AreaId): MutableList<WalkPosition> {
        val boundary = mutableListOf<WalkPosition>()
        boundary += start

        var nextStep = WalkPosition(1, 0)
        var next = start.add(nextStep)
        while (next != start) {
            if (FTTBot.bwem.data.mapData.isValid(next) && FTTBot.bwem.data.getMiniTile(next).areaId == id) {
                if (boundary.last() != next) boundary += next
                nextStep = WalkPosition(nextStep.y, -nextStep.x)
            } else {
                nextStep = WalkPosition(-nextStep.y, nextStep.x)
            }
            next = next.add(nextStep)
        }
        return boundary
    }

}