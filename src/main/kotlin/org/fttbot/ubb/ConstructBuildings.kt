package org.fttbot.ubb

import org.fttbot.Board.resources
import org.fttbot.ConstructionPosition
import org.fttbot.Resources
import org.fttbot.info.UnitQuery
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.SpawningPool
import org.openbw.bwapi4j.unit.Worker
import kotlin.math.min

class BuildSpawingPool : Utility {
    override val utility: Double
        get() = if (required()) min(1.0, Utilities.moreLingsUtility * 1.1) else 0.0

    private fun required() = UnitQuery.myUnits.none { it is SpawningPool }

    private var builder : Worker? = null

    override fun process() {
        if (!required()) return

        if (!resources.canAfford(UnitType.Zerg_Spawning_Pool)) {
            resources.reserve(UnitType.Zerg_Spawning_Pool)
            return
        }
        val position = ConstructionPosition.findPositionFor(UnitType.Zerg_Spawning_Pool) ?: return
        val workers = resources.units.filterIsInstance<Worker>().sortedBy { it.getDistance(position.toPosition()) }
        builder = if (workers.contains(builder)) builder else workers.firstOrNull { it.buildType == UnitType.Zerg_Spawning_Pool }
                ?: workers.firstOrNull { it.isIdle } ?: workers.firstOrNull { it.isGatheringMinerals }
                ?: workers.firstOrNull { it.isGatheringGas } ?: return
        resources.reserveUnit(builder!!)
        if (builder!!.buildType != UnitType.Zerg_Spawning_Pool) {
            builder!!.build(position, UnitType.Zerg_Spawning_Pool)
        }
    }

    companion object : UtilityProvider {
        override fun invoke(): List<Utility> = listOf(BuildSpawingPool())
    }
}