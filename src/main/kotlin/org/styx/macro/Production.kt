package org.styx.macro

import bwapi.TechType
import bwapi.TilePosition
import bwapi.UnitType
import bwapi.UpgradeType
import org.styx.*
import org.styx.Styx.buildPlan
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
        Styx.resources.availableUnits.filter { it.unitType.isWorker }.minBy { eval(it) }
    }
    private val costLock = UnitCostLock(type)
    private var waitFrames = 0

    override fun performTick(): NodeStatus {
        at?.let {
            val targetPos = it.toPosition() + type.dimensions / 2
            val candidate = units.my(type).nearest(targetPos.x, targetPos.y)
            if (candidate?.tilePosition == it)
                return if (candidate.isCompleted) NodeStatus.DONE else NodeStatus.RUNNING
            if (workerLock.unit?.canBuildHere(it, type) == false)
                at = null
        }
        buildPlan.plannedUnits += type
        val buildAt = at ?: ConstructionPosition.findPositionFor(type) ?: return NodeStatus.RUNNING
        at = buildAt
        workerLock.acquire()
        val buildPosition = buildAt.toPosition() + type.dimensions / 2
        costLock.futureFrames = workerLock.unit?.framesToTravelTo(buildPosition) ?: 0
        costLock.acquire()
        val worker = workerLock.unit ?: return NodeStatus.RUNNING
        if (costLock.satisfied)
            BasicActions.build(worker, type, buildAt)
        else if (costLock.willBeSatisfied || waitFrames > 0) {
            waitFrames--;
            BasicActions.move(worker, buildPosition)
            if (costLock.willBeSatisfied) waitFrames = 48;
        } else
            workerLock.release()
        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        workerLock.reset()
        at = null
    }
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
        if (dependency.tick() != NodeStatus.DONE)
            return NodeStatus.RUNNING
        if (trainerLock.unit?.unitType == type)
            return NodeStatus.DONE
        trainerLock.acquire()
        if (trainerLock.unit?.buildType == type)
            return NodeStatus.RUNNING
        buildPlan.plannedUnits += type
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
}

class Get(private val amount: Int, val type: UnitType) : MemoLeaf() {
    private val children = ArrayDeque<BTNode>()

    override fun performTick(): NodeStatus {
        val missingOrIncompleteAnnotation = max(0, amount - units.my(type).count { it.isCompleted })
        if (missingOrIncompleteAnnotation == 0) {
            children.clear()
            return NodeStatus.DONE
        }
        val remaining = max(0, amount - units.my(type).size - buildPlan.plannedUnits.count { it == type })
        if (remaining == 0) {
            return NodeStatus.RUNNING
        }
        val expectedChildCount = min(
                units.my(type.whatBuilds().first).count() + units.myPending.count { it.unitType == type }, if (type.isTwoUnitsInOneEgg) (remaining + 1) / 2 else remaining)
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