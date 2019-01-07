package org.styx

import bwapi.Unit

class Geography {
    val myBases: List<Unit> by LazyOnFrame {
        Context.map.bases.mapNotNull { base ->
            val myClosestBase = Context.find.myResourceDepots.minBy { it.tilePosition.getDistance(base.location) }
                    ?: return@mapNotNull null
            if (myClosestBase.tilePosition.getDistance(base.location) <= 5)
                myClosestBase
            else
                null
        }
    }
}