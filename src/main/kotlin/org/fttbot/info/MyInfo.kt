package org.fttbot.info

import bwem.area.Area
import org.fttbot.FTTBot
import org.fttbot.LazyOnFrame
import org.fttbot.ProductionBoard
import org.fttbot.ResourcesBoard
import org.openbw.bwapi4j.unit.*
import kotlin.math.max

object MyInfo {
    var gasPerFrame = 0.0
    var mineralsPerFrame = 0.0
    var lastGas = 0
    var lastMinerals = 0
    var lastFrame = 0
    val unitStatus = mutableMapOf<PlayerUnit, String>()

    val occupiedAreas by LazyOnFrame<Map<Area, List<PlayerUnit>>> {
        UnitQuery.myUnits.filter { it is Attacker && it !is Worker }
                .groupBy { FTTBot.bwem.getArea(it.tilePosition) }
    }

    val myBases: List<ResourceDepot> by LazyOnFrame {
        FTTBot.bwem.bases.mapNotNull { base ->
            val myClosestBase = UnitQuery.my<ResourceDepot>().minBy { it.tilePosition.getDistance(base.location) }
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
            } + ProductionBoard.pendingUnits.sumBy { it.supplyProvided() } + ResourcesBoard.supply


    fun step() {
        val frameDelta = FTTBot.frameCount - lastFrame
        if (frameDelta > 0) {
            val mineralDelta = max(0, FTTBot.self.gatheredMinerals() - lastMinerals).toDouble() / frameDelta
            val gasDelta = max(0, FTTBot.self.gatheredGas() - lastGas).toDouble() / frameDelta
            mineralsPerFrame = 0.998 * mineralsPerFrame + 0.002 * mineralDelta
            gasPerFrame = 0.998 * gasPerFrame + 0.002 * gasDelta
        }
        lastFrame = FTTBot.frameCount
        lastGas = FTTBot.self.gatheredGas()
        lastMinerals = FTTBot.self.gatheredMinerals()
        unitStatus.clear()
    }

    fun reset() {
        lastGas = FTTBot.self.gas()
        lastMinerals = FTTBot.self.minerals()
        lastFrame = FTTBot.frameCount
    }
}