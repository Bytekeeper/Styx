package org.fttbot.info

import bwem.area.Area
import org.fttbot.Board
import org.fttbot.FTTBot
import org.fttbot.LazyOnFrame
import org.openbw.bwapi4j.unit.*

object MyInfo {
    val occupiedAreas by LazyOnFrame<Map<Area, List<PlayerUnit>>> {
        UnitQuery.myUnits.filter { it is Attacker && it !is Worker }
                .groupBy { FTTBot.bwem.getArea(it.tilePosition) }
    }

    val myBases: List<ResourceDepot> by LazyOnFrame {
        FTTBot.bwem.bases.mapNotNull { base ->
            val myClosestBase = UnitQuery.myBases.minBy { it.tilePosition.getDistance(base.location) }
                    ?: return@mapNotNull null
            if (myClosestBase.tilePosition.getDistance(base.location) > 5)
                null
            else
                myClosestBase as ResourceDepot
        }
    }

    fun pendingSupply(): Int = UnitQuery.myUnits
            .filter {
                !it.isCompleted
            }
            .sumBy {
                ((it as? SupplyProvider)?.supplyProvided() ?: 0) +
                        ((it as? Egg)?.buildType?.supplyProvided() ?: 0)
            } + Board.pendingUnits.sumBy { it.supplyProvided() } + Board.resources.supply
}