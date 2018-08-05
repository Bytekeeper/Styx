package org.fttbot.ubb

import org.fttbot.Board.resources
import org.fttbot.FTTBot
import org.fttbot.Resources
import org.fttbot.info.UnitQuery
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.unit.Larva

class BuildWorker : Utility {
    override val utility: Double
        get() = Utilities.moreWorkersUtility

    override fun process() {
        val worker = FTTBot.self.race.worker
        if (resources.canAfford(worker)) {
            resources.reserve(worker)
            val trainer = resources.units.firstOrNull { it is Larva } as? Larva ?: return
            resources.reserveUnit(trainer)
            trainer.morph(worker)
        } else
            resources.reserve(worker)
    }

    companion object : UtilityProvider {
        override fun invoke(): List<Utility> = UnitQuery.myUnits.filterIsInstance<Larva>().map { BuildWorker() }
    }
}

class BuildSupply : Utility {
    override val utility: Double
        get() = Utilities.moreSupplyUtility

    override fun process() {
        val supply = FTTBot.self.race.supplyProvider
        if (resources.canAfford(supply)) {
            val trainer = resources.units.firstOrNull { it is Larva } as? Larva ?: return
            resources.reserve(supply)
            trainer.morph(supply)
        } else
            resources.reserve(supply)
    }

    companion object : UtilityProvider {
        override fun invoke(): List<Utility> = UnitQuery.myUnits.filterIsInstance<Larva>().map { BuildSupply() }
    }
}