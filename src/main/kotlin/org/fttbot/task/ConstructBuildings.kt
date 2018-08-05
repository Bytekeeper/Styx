package org.fttbot.task

import org.fttbot.Board.pendingUnits
import org.fttbot.Board.resources
import org.fttbot.ConstructionPosition
import org.fttbot.info.UnitQuery
import org.fttbot.info.findWorker
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.Worker
import kotlin.math.min

class Construct(val type: UnitType, val utilityProvider: () -> Double = { 1.0 }) : Task() {
    override val utility: Double get() = utilityProvider()

    private var builderLock = UnitLock<Worker>()
    private var buildPosition: TilePosition? = null

    private val dependencies = ParallelTask(ManagedTaskProvider<UnitType>({
        type.requiredUnits()
                .filter { it.isBuilding && UnitQuery.myUnits.none { u -> u.isA(it) } } -
                pendingUnits
    }) { Construct(it) })

    override fun toString(): String = "Construct $type"

    override fun reset() {
        buildPosition = null
        builderLock.release()
    }

    override fun processInternal(): TaskStatus {
        pendingUnits.add(type)

        val buildingAtPosition = UnitQuery.myUnits.firstOrNull { it.tilePosition == buildPosition && it is Building }
        if (buildingAtPosition?.isA(type) == true) return TaskStatus.DONE
        if (buildingAtPosition != null) {
            buildPosition = null
        }

        dependencies.process().whenFailed { return TaskStatus.FAILED }

        if (!resources.acquireFor(type)) return TaskStatus.RUNNING

        buildPosition = buildPosition ?: ConstructionPosition.findPositionFor(type) ?: return TaskStatus.FAILED

        val worker = builderLock.acquire {
            findWorker(buildPosition!!.toPosition())
        } ?: return TaskStatus.RUNNING
        if (worker.buildType != type) {
            worker.build(buildPosition!!, type)
        }
        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {
        val taskList = listOf(
                Construct(UnitType.Zerg_Spawning_Pool, { min(1.0, Utilities.moreLingsUtility * 1.1) }),
                Construct(UnitType.Zerg_Extractor, Utilities::moreGasUtility).repeat(),
                Construct(UnitType.Zerg_Hatchery, Utilities::moreTrainersUtility).repeat()
        )

        override fun invoke(): List<Task> = taskList
    }
}