package org.fttbot

import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.unit.Base
import org.openbw.bwapi4j.unit.PlayerUnit

object Info {
    val myBases: List<Base> by LazyOnFrame {
        UnitQuery.myUnits
                .filterIsInstance(Base::class.java)
                .filter { base -> FTTBot.bwem.bases.any { it.location.getDistance((base as PlayerUnit).tilePosition) <= 2 } }
    }
}