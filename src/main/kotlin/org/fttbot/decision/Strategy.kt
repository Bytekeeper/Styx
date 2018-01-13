package org.fttbot.decision

import org.fttbot.info.EnemyState
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.unit.Armed
import org.openbw.bwapi4j.unit.ComsatStation
import org.openbw.bwapi4j.unit.MobileUnit
import kotlin.math.min

object StrategyUF {
    fun needMobileDetection() : Double {
        if (!EnemyState.hasInvisibleUnits) return 0.0
        return min(1.0, UnitQuery.myUnits.count { it is Armed } /
                (UnitQuery.myUnits.count { it is ComsatStation || it is MobileUnit && it.isDetector } * 40.0 + 40))
    }
}