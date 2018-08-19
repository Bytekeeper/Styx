package org.fttbot.task

import org.fttbot.ProductionBoard.pendingUnits
import org.fttbot.ConstructionPosition
import org.fttbot.ResourcesBoard
import org.fttbot.info.UnitQuery
import org.fttbot.info.findWorker
import org.fttbot.plus
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

    private val builderLock = UnitLock<Worker>()
    private val morphLock = UnitLock<Building>()
    private val morphDependency: ConstructBuilding by subtask { ConstructBuilding(type.whatBuilds().first, utilityProvider, at) }
    private var buildPosition: TilePosition? = null
    private lateinit var moveToBuildPosition: Move

    private val dependencies: Task by subtask { EnsureUnitDependencies(type) }
    private val haveGas: Task by subtask { HaveGas(type.gasPrice()) }

    override fun toString(): String = "ConstructBuilding $type"

    override fun reset() {
        buildPosition = null
        builderLock.release()
        morphLock.release()
    }

    override fun processInternal(): TaskStatus {
        val buildingAtPosition = buildPosition?.getBuilding()
        if (buildingAtPosition?.isA(type) == true)
            return TaskStatus.DONE
        pendingUnits.add(type)

        if (buildingAtPosition != null) {
            if (at != null) return TaskStatus.FAILED
            buildPosition = null
        }

        dependencies.process().whenFailed { return TaskStatus.FAILED }
        haveGas.process().whenNotDone { return it }

        if (!ResourcesBoard.acquireFor(type)) {
            return TaskStatus.RUNNING
        }

        buildPosition = at ?: buildPosition ?: ConstructionPosition.findPositionFor(type) ?: return TaskStatus.FAILED

        if (type.whatBuilds().first.isWorker) {
            val worker = builderLock.acquire {
                findWorker(buildPosition!!.toPosition(), maxRange = 4000.0)
            } ?: return TaskStatus.RUNNING

            if (builderLock.reassigned) {
                moveToBuildPosition = Move(worker, worker.position, 96)
            }
            moveToBuildPosition.to = buildPosition!!.toPosition() + Position(type.width() / 2, type.height() / 2)

            val moveStatus = moveToBuildPosition.process()
            if (moveStatus != TaskStatus.DONE) return moveStatus
            if (worker.buildType != type || worker.targetPosition.toTilePosition().getDistance(buildPosition) > 3) {
                worker.build(buildPosition, type)
            }
        } else {
            val morphable = morphLock.acquire {
                UnitQuery.myBuildings.firstOrNull { it.isA(type.whatBuilds().first) }
            } ?: return morphDependency.process()

            if (morphable.remainingBuildTime == 0) {
                (morphable as Morphable).morph(type)
            }
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