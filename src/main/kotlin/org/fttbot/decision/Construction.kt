package org.fttbot.decision

import com.badlogic.gdx.math.MathUtils.clamp
import org.fttbot.FTTBot
import org.fttbot.FTTBot.self
import org.fttbot.behavior.ProductionBoard
import org.fttbot.info.EnemyState
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Armed
import org.openbw.bwapi4j.unit.ComsatStation
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.TrainingFacility
import kotlin.math.min

object Construction {
    val trainingFacitilySupplyFactor = 2

    private fun pending(unitType: UnitType) = ProductionBoard.orderedConstructions.count { !it.started && it.type == unitType } +
            UnitQuery.myUnits.count { !it.isCompleted && it.isBeingConstructed } +
            ProductionBoard.queue.count { it is ProductionBoard.UnitItem && it.type == unitType }

    fun supplyNeeded() : Double {
        if (FTTBot.self.supplyTotal() >= 400) return 0.0
        val supplyEstimated = self.supplyTotal() - self.supplyUsed() -
                UnitQuery.myUnits.filter { it is TrainingFacility }.count() * trainingFacitilySupplyFactor
        val supplyProvider = FTTBot.self.race.supplyProvider
        val supplyDelta = pending(supplyProvider) * supplyProvider.supplyProvided() + supplyEstimated
        return clamp(-supplyDelta / 10.0 + 0.3, 0.0, 1.0)
    }
}