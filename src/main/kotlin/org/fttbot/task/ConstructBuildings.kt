package org.fttbot.task

import org.fttbot.*
import org.fttbot.ProductionBoard.pendingUnits
import org.fttbot.info.UnitQuery
import org.fttbot.info.findWorker
import org.fttbot.info.isMorphedFromOtherBuilding
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.org.apache.commons.lang3.mutable.MutableInt
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.GasMiningFacility
import org.openbw.bwapi4j.unit.Morphable
import org.openbw.bwapi4j.unit.Worker
import kotlin.math.ceil

fun TilePosition.getBuilding() = UnitQuery.myUnits.firstOrNull { it.tilePosition == this@getBuilding && it is Building } as? Building

class Build(val type: UnitType, val utilityProvider: UtilityProvider = { 1.0 }, var at: TilePosition? = null) : Task() {
    override val utility: Double get() = utilityProvider()

    private val inProgressLock = UnitLocked<Building>(this)
    private val builderLock = UnitLocked<Worker>(this)
    private val morphLock = UnitLocked<Morphable>(this)
    private val morphDependency by SubTask {
        Build(type.whatBuilds().unitType, utilityProvider, at)
    }
    private var buildPositionLock = Locked<TilePosition>(this)
    private val moveToBuildPositionLock = builderLock.map<Move>()

    private val dependencies: Task by SubTask { EnsureUnitDependencies(type) }
    private val haveGas: Task by SubTask { HaveGas(type.gasPrice()) }

    init {
        assert(type.isBuilding) { "Can't building a normal unit!" }
    }

    override fun toString(): String = "Build $type"

    override fun processInternal(): TaskStatus {
        inProgressLock.compute {
            (if (type.isMorphedFromOtherBuilding())
                ResourcesBoard.units.firstOrNull { u -> u.id == morphLock.entity?.id && u.type == type }
            else if (type.isRefinery && builderLock.entity?.exists() == false)
                UnitQuery.my<GasMiningFacility>().inRadius(builderLock.entity!!, 64).firstOrNull { !it.isCompleted }
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
        val dependencyStatus = processAll(dependencies, haveGas).andWhenFailed {
            return TaskStatus.FAILED
        }

        val buildPosition = buildPositionLock.compute {
            at ?: ConstructionPosition.findPositionFor(type)
        } ?: return TaskStatus.FAILED

        val worker = builderLock.compute {
            findWorker(buildPosition.toPosition(), maxRange = Double.MAX_VALUE)
        } ?: return TaskStatus.RUNNING

        val length = MutableInt()
        FTTBot.bwem.getPath(worker.position, buildPosition.toPosition(), length)
        val futureFrames = ceil(length.value / worker.type.topSpeed()).toInt()

        if (!ResourcesBoard.canAfford(type, futureFrames)) {
            ResourcesBoard.acquireFor(type)
            buildPositionLock.release()
            builderLock.release()
            return TaskStatus.RUNNING
        }
        val resourcesAcquired = ResourcesBoard.acquireFor(type)
        if (dependencyStatus != TaskStatus.DONE) return dependencyStatus

        val moveToBuildPosition = moveToBuildPositionLock.compute { Move(it, it.position, 96) }!!
        moveToBuildPosition.to = buildPosition.toPosition() + Position(type.width() / 2, type.height() / 2)

        val moving = moveToBuildPosition.process()
        if (moving == TaskStatus.FAILED) {
            buildPositionLock.release()
            return TaskStatus.RUNNING
        } else if (moving == TaskStatus.RUNNING)
            return TaskStatus.RUNNING

        if (!resourcesAcquired) {
            return TaskStatus.RUNNING
        }

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

        val dependencyStatus = processAll(dependencies, haveGas).andWhenFailed { return TaskStatus.FAILED }

        if (!ResourcesBoard.acquireFor(type)) {
            return TaskStatus.RUNNING
        }
        if (dependencyStatus != TaskStatus.DONE) return dependencyStatus

        toMorph.morph(type)
        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {
        val taskList = listOf(
                HaveBuilding(UnitType.Zerg_Spawning_Pool, { 0.7 }),
                Build(UnitType.Zerg_Sunken_Colony, { 0.6 }),
                Build(UnitType.Zerg_Hatchery, Utilities::moreTrainersUtility).nvr()
//                ,
//                Build(UnitType.Zerg_Extractor, Utilities::moreGasUtility).nvr()
        )

        override fun invoke(): List<Task> = taskList
    }
}