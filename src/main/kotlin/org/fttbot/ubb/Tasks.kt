package org.fttbot.ubb

import org.fttbot.Board.resources
import org.fttbot.Resources
import org.fttbot.info.UnitQuery
import org.fttbot.info.closestTo
import org.openbw.bwapi4j.unit.Worker

class GatherMinerals : Utility {
    override val utility: Double
        get() = 0.1

    override fun process() {
        val workers = resources.units.filterIsInstance<Worker>()
        val worker = workers.firstOrNull { it.isGatheringMinerals }
                ?: workers.firstOrNull { it.isIdle }
                ?: workers.firstOrNull { it.isGatheringGas }
                ?: return
        if (!worker.isGatheringMinerals) {
            val mineral = UnitQuery.minerals.closestTo(worker) ?: return
            resources.reserveUnit(worker)
            worker.gather(mineral)
        } else
            resources.reserveUnit(worker)
    }

    companion object : UtilityProvider {
        override fun invoke(): List<Utility> = UnitQuery.myWorkers.map { GatherMinerals() }
    }
}