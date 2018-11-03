package org.fttbot.task

import org.fttbot.*
import org.fttbot.info.UnitQuery
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Egg
import org.openbw.bwapi4j.unit.Larva
import org.openbw.bwapi4j.unit.Morphable

class Train(val type: UnitType, val utilityProvider: () -> Double = { 1.0 }, var at: TilePosition? = null) : Task() {
    override val utility: Double
        get() = utilityProvider()

    private val trainerLock = UnitLocked<Morphable>(this)
    private val dependencies: Task by LazyTask { EnsureUnitDependencies(type) }

    init {
        assert(!type.isBuilding) { "Can't train a building!" }
    }

    override fun toString(): String = "Training $type"

    override fun processInternal(): TaskStatus {
        trainerLock.entity?.let { trained ->
            if (!trained.exists()) {
                if (UnitQuery.myUnits.any { it.id == trained.id && it is Egg }) {
                    trainerLock.get()
                    return TaskStatus.RUNNING
                }
                if (UnitQuery.myUnits.any { it.id == trained.id && it.type == type })
                    return TaskStatus.DONE
                return TaskStatus.FAILED
            }
            if (ResourcesBoard.units.contains(trained)) {
                // Relock
                trainerLock.get()
            }
            // Larva might not have started morphing
            if (trained is Larva && trained.trainingSlot?.unitType == type) {
                return TaskStatus.RUNNING;
            }
        }

        ProductionBoard.pendingUnits.add(type)

        val dependencyStatus = dependencies.process()
        if (dependencyStatus == TaskStatus.FAILED)
            return TaskStatus.FAILED

        val resourcesAcquired = ResourcesBoard.acquireFor(type)

        val trainer = trainerLock.compute { ResourcesBoard.completedUnits.firstOrNull { it.type == type.whatBuilds().unitType } as? Morphable }

        if (!resourcesAcquired || dependencyStatus == TaskStatus.RUNNING || trainer == null) return TaskStatus.RUNNING

        if (!trainer.morph(type)) {
            trainerLock.release()
        }
        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {
        val larvas by LazyOnFrame {
            UnitQuery.myUnits.filterIsInstance<Larva>()
        }
        val workers = ItemToTaskMapper({ larvas }, { Train(FTTBot.self.race.worker, Utilities::moreWorkersUtility).nvr() })
        val lings = ItemToTaskMapper({ larvas }, { Train(UnitType.Zerg_Zergling, { Utilities.moreLingsUtility }).nvr() })
        private val hydras = ItemToTaskMapper({ larvas }, { Train(UnitType.Zerg_Hydralisk, { Utilities.moreHydrasUtility }).nvr() })
        private val lurker = ItemToTaskMapper({ larvas }, { Train(UnitType.Zerg_Lurker, { Utilities.moreLurkersUtility }).nvr() })
        val mutas = ItemToTaskMapper({ larvas }, { Train(UnitType.Zerg_Mutalisk, { Utilities.moreMutasUtility }).nvr() })
        val ovis = ItemToTaskMapper({ larvas }, { Train(FTTBot.self.race.supplyProvider, Utilities::moreSupplyUtility).nvr() })

        override fun invoke(): List<Task> = workers() + lings() + hydras() + lurker()
    }
}