package org.fttbot

import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.unit.SupplyProvider
import kotlin.math.min

object Info {
    val pendingSupply get() = UnitQuery.myUnits.filter { !it.isCompleted }
            .filterIsInstance(SupplyProvider::class.java).sumBy { it.supplyProvided() } +
            ProductionQueue.pending.filterIsInstance(BOUnit::class.java).mapNotNull { it.type as? SupplyProvider }.sumBy { it.supplyProvided() }

    val totalSupplyWithPending get() = min(FTTBot.self.supplyTotal() + pendingSupply, 400)
}