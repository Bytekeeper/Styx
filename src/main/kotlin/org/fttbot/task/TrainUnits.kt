package org.fttbot.task

import org.fttbot.Board
import org.fttbot.Board.resources
import org.fttbot.FTTBot
import org.fttbot.LazyOnFrame
import org.fttbot.info.UnitQuery
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Larva

class Train(val type: UnitType, val utilityProvider: () -> Double) : Task() {
    override val utility: Double
        get() = utilityProvider()

    val trainerLock = UnitLock<Larva>()

    override fun toString(): String = "Training $type"

    override fun reset() {
        trainerLock.release()
    }

    override fun processInternal(): TaskStatus {
        if (trainerLock.currentUnit?.isA(type) == true) return TaskStatus.DONE
        Board.pendingUnits.add(type)

        if (!resources.acquireFor(type))
            return TaskStatus.RUNNING

        val trainer = trainerLock.acquire { resources.units.firstOrNull { it is Larva } as? Larva }
                ?: return TaskStatus.RUNNING
        trainer.morph(type)
        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {
        val larvas by LazyOnFrame {
            UnitQuery.myUnits.filterIsInstance<Larva>()
        }
        private val workers = ManagedTaskProvider({ larvas }, { Train(FTTBot.self.race.worker, Utilities::moreWorkersUtility).repeat() })
        private val lings = ManagedTaskProvider({ larvas }, { Train(UnitType.Zerg_Zergling, { Utilities.moreLingsUtility * 2.0 }).repeat() })
        private val ovis = ManagedTaskProvider({ larvas }, { Train(FTTBot.self.race.supplyProvider, Utilities::moreSupplyUtility).repeat() })

        override fun invoke(): List<Task> = workers() + lings() + ovis()
    }
}