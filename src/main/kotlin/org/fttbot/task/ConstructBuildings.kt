package org.fttbot.task

import org.fttbot.*
import org.fttbot.ProductionBoard.pendingUnits
import org.fttbot.info.UnitQuery
import org.fttbot.info.findWorker
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.Morphable
import org.openbw.bwapi4j.unit.Worker

fun TilePosition.getBuilding() = UnitQuery.myUnits.firstOrNull { it.tilePosition == this@getBuilding && it is Building }

class ConstructBuilding(val type: UnitType, val utilityProvider: UtilityProvider = { 1.0 }, var at: TilePosition? = null) : Task() {
    override val utility: Double get() = utilityProvider()

    private val builderLock = UnitLocked<Worker>(this)
    private val morphLock = UnitLocked<Building>(this)
    private val morphDependency: ConstructBuilding by subtask { ConstructBuilding(type.whatBuilds().unitType, utilityProvider, at) }
    private var buildPositionLock = Locked<TilePosition>(this)
    private val moveToBuildPositionLock = builderLock.map<Move>();

    private val dependencies: Task by subtask { EnsureUnitDependencies(type) }
    private val haveGas: Task by subtask { HaveGas(type.gasPrice()) }

    override fun toString(): String = "ConstructBuilding $type"

    override fun processInternal(): TaskStatus {
        val buildingAtPosition = buildPositionLock.entity?.getBuilding()
        if (buildingAtPosition?.type == type)
            return TaskStatus.DONE

        if (buildingAtPosition != null) {
            if (at != null) return TaskStatus.FAILED
        }

        pendingUnits.add(type)
        processAll(dependencies, haveGas)
                .andWhenNotDone { return it }

        if (!ResourcesBoard.acquireFor(type)) {
            return TaskStatus.RUNNING
        }

        if (at != null && at != buildPositionLock.entity) {
            buildPositionLock.set(at!!)
        }
        if (type.whatBuilds().unitType.isWorker) {
            return buildWithWorker()
        } else {
            return morphExisting()
        }
    }

    fun morphExisting(): TaskStatus {
        val morphable = morphLock.compute {
            UnitQuery.myBuildings.firstOrNull { it.type == type.whatBuilds().unitType }
        } ?: return morphDependency.process()

        if (morphable.remainingBuildTime == 0) {
            (morphable as Morphable).morph(type)
        }
        return TaskStatus.RUNNING
    }

    fun buildWithWorker(): TaskStatus {
        val buildPosition = buildPositionLock.compute { ConstructionPosition.findPositionFor(type) }
                ?: return TaskStatus.FAILED
        val worker = builderLock.compute {
            findWorker(buildPosition.toPosition(), maxRange = 4000.0)
        } ?: return TaskStatus.RUNNING


        val moveToBuildPosition = moveToBuildPositionLock.compute { Move(it, it.position, 96) }!!
        moveToBuildPosition.to = buildPosition.toPosition() + Position(type.width() / 2, type.height() / 2)

        moveToBuildPosition.process().andWhenNotDone { return it }
        if (worker.buildType != type || worker.targetPosition.toTilePosition().getDistance(buildPosition) > 3) {
            worker.build(buildPosition, type)
        }
        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {
        val taskList = listOf(
                ConstructBuilding(UnitType.Zerg_Spawning_Pool, { 0.7 }),
                ConstructBuilding(UnitType.Zerg_Hatchery, Utilities::moreTrainersUtility).repeat()
        )

        override fun invoke(): List<Task> = taskList
    }
}