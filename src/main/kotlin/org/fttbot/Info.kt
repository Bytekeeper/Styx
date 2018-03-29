package org.fttbot

import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.unit.Base
import org.openbw.bwapi4j.unit.PlayerUnit

object Info {
    val myBases: List<Base> by LazyOnFrame {
        FTTBot.bwem.bases.mapNotNull { base ->
            val myClosestBase = UnitQuery.myBases.minBy { it.tilePosition.getDistance(base.location) }
                    ?: return@mapNotNull null
            if (myClosestBase.tilePosition.getDistance(base.location) > 5)
                null
            else
                myClosestBase as Base
        }
    }
}