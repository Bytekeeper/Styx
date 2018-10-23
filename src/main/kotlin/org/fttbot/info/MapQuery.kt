package org.fttbot.info

import bwem.area.typedef.AreaId
import org.fttbot.FTTBot
import org.fttbot.toCoordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.MultiPoint
import org.openbw.bwapi4j.TilePosition

object MapQuery {
    val polys = mutableListOf<MultiPoint>()
    private val geometryFactory = GeometryFactory()

    fun reset() {
        if (FTTBot.bwemInitializer.isDone && polys.isEmpty()) {
            FTTBot.bwem.areas.forEach {
                for (y in it.topLeft.y..it.bottomRight.y) {
                    for (x in it.topLeft.x..it.bottomRight.x) {
                        val wp = TilePosition(x, y)
                        if (FTTBot.bwem.data.mapData.isValid(wp) && FTTBot.bwem.data.getTile(wp).areaId == it.id) {
                            val multiPoint = geometryFactory.createMultiPointFromCoords(squareTrace(wp, it.id).map { it.toPosition().toCoordinate() }.toTypedArray())
                            multiPoint.userData = it
                            polys += multiPoint
                            return@forEach
                        }
                    }
                }
            }
        }
    }

    private fun squareTrace(start: TilePosition, id: AreaId): MutableList<TilePosition> {
        val boundary = mutableListOf<TilePosition>()
        boundary += start

        var nextStep = TilePosition(1, 0)
        var next = start.add(nextStep)
        while (next != start) {
            if (FTTBot.bwem.data.mapData.isValid(next) && FTTBot.bwem.data.getTile(next).areaId == id) {
                if (!FTTBot.bwem.data.getTile(next).isWalkable) {
                    println("doh")
                }
                if (boundary.last() != next) boundary += next
                nextStep = TilePosition(nextStep.y, -nextStep.x)
            } else {
                nextStep = TilePosition(-nextStep.y, nextStep.x)
            }
            next = next.add(nextStep)
        }
        return boundary
    }

}