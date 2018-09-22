package org.fttbot.task

import org.fttbot.*
import org.fttbot.info.UnitQuery
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Larva

class Train(val type: UnitType, val utilityProvider: () -> Double, var at: TilePosition? = null) : Task() {
    override val utility: Double
        get() = utilityProvider()

    private val trainerLock = UnitLocked<Larva>(this)
    private val dependencies: Task by subtask { EnsureUnitDependencies(type) }

    override fun toString(): String = "Training $type"

    override fun processInternal(): TaskStatus {
        if (trainerLock.entity?.type == type) return TaskStatus.DONE
        ProductionBoard.pendingUnits.add(type)

        dependencies.process().andWhenFailed { return TaskStatus.FAILED }

        if (!ResourcesBoard.acquireFor(type))
            return TaskStatus.RUNNING

        val trainer = trainerLock.compute { ResourcesBoard.units.firstOrNull { it is Larva } as? Larva }
                ?: return TaskStatus.RUNNING

        trainer.morph(type)
        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {
        val larvas by LazyOnFrame {
            UnitQuery.myUnits.filterIsInstance<Larva>()
        }
        private val workers = ManagedTaskProvider({ larvas }, { Train(FTTBot.self.race.worker, Utilities::moreWorkersUtility).repeat() })
        private val lings = ManagedTaskProvider({ larvas }, { Train(UnitType.Zerg_Zergling, { Utilities.moreLingsUtility }).repeat() })
        private val hydras = ManagedTaskProvider({ larvas }, { Train(UnitType.Zerg_Hydralisk, { Utilities.moreHydrasUtility }).repeat() })
        private val lurker = ManagedTaskProvider({ larvas }, { Train(UnitType.Zerg_Lurker, { Utilities.moreLurkersUtility }).repeat() })
        private val mutas = ManagedTaskProvider({ larvas }, { Train(UnitType.Zerg_Mutalisk, { Utilities.moreMutasUtility }).repeat() })
        private val ovis = ManagedTaskProvider({ larvas }, { Train(FTTBot.self.race.supplyProvider, Utilities::moreSupplyUtility).repeat() })

        override fun invoke(): List<Task> = workers() + lings() + ovis() + hydras() + mutas() + lurker()
    }
}