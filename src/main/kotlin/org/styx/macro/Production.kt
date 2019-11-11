package org.styx.macro

import bwapi.TechType
import bwapi.TilePosition
import bwapi.UnitType
import bwapi.UpgradeType
import org.bk.ass.manage.GMS
import org.styx.*
import org.styx.Styx.buildPlan
import org.styx.Styx.diag
import org.styx.Styx.economy
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.action.BasicActions
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Trigger build of unit, don't wait for completion
 */
class Build(val type: UnitType) : MemoLeaf() {
    private var at: TilePosition? = null
    private val workerLock = UnitLock {
        val eval = closeTo(at!!.toPosition())
        resources.availableUnits.filter { it.unitType.isWorker }.minBy { eval(it) }
    }
    private val costLock = UnitCostLock(type)
    private var hysteresisFrames = 0
    private val dependency = Par("Dependencies for ${type}",
            *type.requiredUnits()
                    .filter { (type, _) -> type != UnitType.Zerg_Larva }
                    .map { (type, amount) ->
                        Get(amount, type)
                    }.toTypedArray())

    override fun performTick(): NodeStatus {
        at?.let {
            val targetPos = it.toPosition() + type.dimensions / 2
            val candidate = units.my(type).nearest(targetPos.x, targetPos.y)
            if (candidate?.tilePosition == it)
                return if (candidate.isCompleted) NodeStatus.DONE else NodeStatus.RUNNING
            if (workerLock.unit?.canBuildHere(it, type) == false)
                at = null
        }
        if (dependency.tick() == NodeStatus.FAILED)
            return NodeStatus.FAILED
        val buildAt = at ?: ConstructionPosition.findPositionFor(type) ?: run {
            buildPlan.plannedUnits += PlannedUnit(type)
            return NodeStatus.RUNNING
        }
        at = buildAt
        workerLock.acquire()
        val buildPosition = buildAt.toPosition() + type.dimensions / 2
        val travelFrames = workerLock.unit?.framesToTravelTo(buildPosition) ?: 0
        costLock.futureFrames = travelFrames + hysteresisFrames
        costLock.acquire()
        val worker = workerLock.unit ?: run {
            buildPlan.plannedUnits += PlannedUnit(type)
            return NodeStatus.RUNNING
        }
        if (costLock.satisfied) {
            buildPlan.plannedUnits += PlannedUnit(type, 0)
            hysteresisFrames = 0
            BasicActions.build(worker, type, buildAt)
        } else if (costLock.willBeSatisfied) {
            buildPlan.plannedUnits += PlannedUnit(type, travelFrames)
            if (hysteresisFrames == 0 && Config.logEnabled) {
                diag.traceLog("Build ${type} with ${worker.diag()} at ${buildAt.toWalkPosition().diag()} - GMS: ${resources.availableGMS}, " +
                        "actual resources: ${economy.currentResources}, " +
                        "frames to target: $travelFrames, plan queue: ${buildPlan.plannedUnits}")
            }
            hysteresisFrames--;
            BasicActions.move(worker, buildPosition)
            if (costLock.willBeSatisfied) hysteresisFrames = Config.productionHysteresisFrames
        } else {
            buildPlan.plannedUnits += PlannedUnit(type)
            if (hysteresisFrames > 0) {
                diag.traceLog("Postponed build ${type} with ${worker.diag()} at ${buildAt.toWalkPosition().diag()}  - GMS: ${resources.availableGMS}, " +
                        "frames to target: $travelFrames, hysteresis frames: " +
                        "$hysteresisFrames")
            }
            hysteresisFrames = 0
            workerLock.release()
        }
        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        workerLock.reset()
        at = null
        hysteresisFrames = 0
    }

    override fun toString(): String = "Build $type at $at"
}

/**
 * Trigger training of unit, don't wait for completion
 */
class Train(private val type: UnitType) : MemoLeaf() {
    private val trainerLock = UnitLock({ it.unitType == type.whatBuilds().first || it.buildType == type }) { Styx.resources.availableUnits.firstOrNull { it.unitType == type.whatBuilds().first } }
    private val costLock = UnitCostLock(type)
    private val dependency = Par("Dependencies for ${type}",
            *type.requiredUnits()
                    .filter { (type, _) -> type != UnitType.Zerg_Larva }
                    .map { (type, amount) ->
                        Get(amount, type)
                    }.toTypedArray())

    override fun performTick(): NodeStatus {
        if (dependency.tick() == NodeStatus.FAILED)
            return NodeStatus.FAILED
        if (trainerLock.unit?.unitType == type)
            return NodeStatus.DONE
        trainerLock.acquire()
        if (trainerLock.unit?.buildType == type)
            return NodeStatus.RUNNING
        buildPlan.plannedUnits += PlannedUnit(type)
        costLock.acquire()
        if (!costLock.satisfied)
            return NodeStatus.RUNNING
        if (type.requiredUnits().keys.any { reqType -> units.mine.none { it.unitType == reqType && it.isCompleted } })
            return NodeStatus.RUNNING
        val trainer = trainerLock.unit ?: return NodeStatus.RUNNING
        BasicActions.train(trainer, type)
        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        dependency.reset()
        trainerLock.reset()
    }

    override fun toString(): String = "Train $type"
}

class Get(private val amount: Int,
          val type: UnitType) : MemoLeaf() {
    private val children = ArrayDeque<BTNode>()

    override fun performTick(): NodeStatus {
        val missingOrIncompleteAnnotation = max(0, amount - units.my(type).count { it.isCompleted })
        if (missingOrIncompleteAnnotation == 0) {
            children.clear()
            return NodeStatus.DONE
        }
        val pendingUnitsFactor = if (type.isTwoUnitsInOneEgg) 2 else 1
        val remaining = max(0, amount - units.my(type).size - buildPlan.plannedUnits.count { it.type == type } * pendingUnitsFactor)
        if (remaining == 0) {
            return NodeStatus.RUNNING
        }

        val builders = units.my(type.whatBuilds().first).count() + determineFutureBuildersToBlockNow()
        val expectedChildCount =
                min(builders + units.myPending.count { it.unitType == type },
                        (remaining + pendingUnitsFactor / 2) / pendingUnitsFactor)
        if (expectedChildCount == 0) return NodeStatus.RUNNING
        repeat(expectedChildCount - children.size) {
            children += if (type.isBuilding) Build(type) else Train(type)
        }
        repeat(children.size - expectedChildCount) {
            children.removeFirst()
        }
        children.removeIf {
            it.tick() != NodeStatus.RUNNING
        }
        return NodeStatus.RUNNING
    }

    // Do we have enough money when the builder arrives? If not, we should lock resources etc.
    private fun determineFutureBuildersToBlockNow(): Int {
        val costPerUnit = GMS.unitCost(type)
        val predictedAdditionalBuilders =
                if (type.whatBuilds().first == UnitType.Zerg_Larva) {
                    var estimatedCost = GMS(0, 0, 0)
                    units.myResourceDepots.filter { it.remainingTrainTime > 0 }
                            .sortedBy { it.remainingTrainTime }
                            .asSequence()
                            .map { it.remainingTrainTime }
                            .takeWhile { frames ->
                                estimatedCost += costPerUnit
                                !(resources.availableGMS + economy.estimatedAdditionalGMSIn(frames)).greaterOrEqual(estimatedCost)
                            }.count()
                } else 0
        return predictedAdditionalBuilders
    }

    override fun toString(): String = "Get $amount ${type.shortName()}"
}

class Upgrade(private val upgrade: UpgradeType, private val level: Int) : MemoLeaf() {
    private val costLock = UpgradeCostLock(upgrade, level)
    private val researcherLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == upgrade.whatUpgrades() } }

    override fun performTick(): NodeStatus {
        if (upgradeIsDone())
            return NodeStatus.DONE
        if (isAlreadyUpgrading())
            return NodeStatus.RUNNING
        costLock.acquire()
        if (costLock.satisfied) {
            researcherLock.acquire()
            val researcher = researcherLock.unit ?: return NodeStatus.RUNNING
            researcher.upgrade(upgrade)
        }
        return NodeStatus.RUNNING
    }

    private fun upgradeIsDone() = Styx.self.getUpgradeLevel(upgrade) == level

    private fun isAlreadyUpgrading() = Styx.self.isUpgrading(upgrade) && Styx.self.getUpgradeLevel(upgrade) == level - 1
}


class Research(private val tech: TechType) : MemoLeaf() {
    private val costLock = TechCostLock(tech)
    private val researcherLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == tech.whatResearches() } }

    override fun performTick(): NodeStatus {
        if (Styx.self.hasResearched(tech))
            return NodeStatus.DONE
        if (Styx.self.isResearching(tech))
            return NodeStatus.RUNNING;
        costLock.acquire()
        if (costLock.satisfied) {
            researcherLock.acquire()
            val researcher = researcherLock.unit ?: return NodeStatus.RUNNING
            researcher.research(tech)
        }
        return NodeStatus.RUNNING
    }

}


class Morph(private val type: UnitType) : MemoLeaf() {
    private val morphLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == type.whatBuilds().first } }
    private val costLock = UnitCostLock(type)

    override fun performTick(): NodeStatus {
        if (morphLock.unit?.unitType == type)
            return NodeStatus.DONE
        costLock.acquire()
        if (costLock.satisfied) {
            morphLock.acquire()
            val unitToMorph = morphLock.unit ?: return NodeStatus.RUNNING
            BasicActions.morph(unitToMorph, type)
        }
        return NodeStatus.RUNNING
    }
}