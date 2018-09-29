package org.fttbot.task

import org.fttbot.*
import org.fttbot.ProductionBoard.pendingUnits
import org.fttbot.info.UnitQuery
import org.fttbot.info.findWorker
import org.fttbot.info.isMorphedFromOtherBuilding
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.Morphable
import org.openbw.bwapi4j.unit.Worker

fun TilePosition.getBuilding() = UnitQuery.myUnits.firstOrNull { it.tilePosition == this@getBuilding && it is Building } as? Building

class ConstructBuilding(val type: UnitType, val utilityProvider: UtilityProvider = { 1.0 }, var at: TilePosition? = null) : Task() {
    override val utility: Double get() = utilityProvider()

    private val inProgressLock = UnitLocked<Building>(this)
    private val builderLock = UnitLocked<Worker>(this)
    private val morphLock = UnitLocked<Morphable>(this)
    private val morphDependency by SubTask {
        ConstructBuilding(type.whatBuilds().unitType, utilityProvider, at)
    }
    private var buildPositionLock = Locked<TilePosition>(this)
    private val moveToBuildPositionLock = builderLock.map<Move>();

    private val dependencies: Task by SubTask { EnsureUnitDependencies(type) }
    private val haveGas: Task by SubTask { HaveGas(type.gasPrice()) }

    init {
        assert(type.isBuilding) { "Can't building a normal unit!" }
    }

    override fun toString(): String = "ConstructBuilding $type"

    override fun processInternal(): TaskStatus {
        inProgressLock.compute {
            (if (type.isMorphedFromOtherBuilding())
                ResourcesBoard.units.firstOrNull { u -> u.id == morphLock.entity?.id && u.type == type }
            else
                ResourcesBoard.units.firstOrNull { u -> u.id == builderLock.entity?.id && u.type == type }) as? Building
        }?.let {
            return if (it.isCompleted) TaskStatus.DONE else TaskStatus.RUNNING
        }

        pendingUnits.add(type)

        return if (type.isMorphedFromOtherBuilding())
            processMorph()
        else
            processBuildWithWorker()
    }

    private fun processBuildWithWorker(): TaskStatus {
        processRequirements().andWhenNotDone { return it }

        val buildPosition = buildPositionLock.compute {
            at ?: ConstructionPosition.findPositionFor(type)
        } ?: return TaskStatus.FAILED

        val worker = builderLock.compute {
            findWorker(buildPosition.toPosition(), maxRange = Double.MAX_VALUE)
        } ?: return TaskStatus.RUNNING

        val moveToBuildPosition = moveToBuildPositionLock.compute { Move(it, it.position, 96) }!!
        moveToBuildPosition.to = buildPosition.toPosition() + Position(type.width() / 2, type.height() / 2)

        moveToBuildPosition.process().andWhenNotDone { return it }
        if (worker.buildType != type || worker.targetPosition.toTilePosition().getDistance(buildPosition) > 3) {
            if (!worker.build(buildPosition, type)) {
                buildPositionLock.release()
            }
        }
        return TaskStatus.RUNNING
    }

    private fun processMorph(): TaskStatus {
        val toMorph = morphLock.compute {
            ResourcesBoard.completedUnits.firstOrNull { u -> u.type == type.whatBuilds().unitType } as? Morphable
        }

        if (toMorph == null) {
            morphDependency.process().andWhenNotDone { return it }
            return TaskStatus.RUNNING
        }

        processRequirements().andWhenNotDone { return it }

        toMorph.morph(type)
        return TaskStatus.RUNNING
    }

    fun processRequirements(): TaskStatus {
        processAll(dependencies, haveGas)
                .andWhenNotDone { return it }

        if (!ResourcesBoard.acquireFor(type)) {
            return TaskStatus.RUNNING
        }
        return TaskStatus.DONE
    }

    companion object : TaskProvider {
        val taskList = listOf(
                HaveBuilding(UnitType.Zerg_Spawning_Pool, { 0.7 }),
                ConstructBuilding(UnitType.Zerg_Sunken_Colony, { 0.6 }),
                ConstructBuilding(UnitType.Zerg_Sunken_Colony, { 0.6 }),
                ConstructBuilding(UnitType.Zerg_Sunken_Colony, { 0.5 }),
                ConstructBuilding(UnitType.Zerg_Sunken_Colony, { 0.5 }),
                ConstructBuilding(UnitType.Zerg_Hatchery, Utilities::moreTrainersUtility).repeat(),
                ConstructBuilding(UnitType.Zerg_Extractor, Utilities::moreGasUtility).neverFail().repeat()
        )

        override fun invoke(): List<Task> = taskList
    }
}