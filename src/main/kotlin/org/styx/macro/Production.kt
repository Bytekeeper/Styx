package org.styx.macro

import bwapi.TechType
import bwapi.TilePosition
import bwapi.UnitType
import bwapi.UpgradeType
import org.styx.*
import org.styx.Styx.buildPlan
import org.styx.Styx.game
import org.styx.action.BasicActions
import java.util.*
import kotlin.math.max

/**
 * Trigger build of unit, don't wait for completion
 */
class Build(val type: UnitType) : MemoLeaf() {
    private var at: TilePosition? = null
    private val workerLock = UnitLock {
        val pos = at!!.toPosition()
        Styx.resources.availableUnits.filter { it.unitType.isWorker }.minBy { it.distanceTo(pos) }
    }
    private val costLock = UnitCostLock(type)

    override fun performTick(): NodeStatus {
        at?.let {
            val targetPos = it.toPosition() + type.dimensions / 2
            val candidate = Styx.units.my(type).nearest(targetPos.x, targetPos.y)
            if (candidate?.tilePosition == it)
                return NodeStatus.DONE
            if (!game.canBuildHere(it, type, workerLock.unit?.unit))
                at = null
        }
        buildPlan.pendingUnits += type
        val buildAt = at ?: ConstructionPosition.findPositionFor(type) ?: return NodeStatus.RUNNING
        at = buildAt
        costLock.acquire()
        workerLock.acquire()
        val worker = workerLock.unit ?: return NodeStatus.RUNNING
        if (costLock.willBeSatisfied)
            BasicActions.build(worker, type, buildAt)
        else
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
    private val trainerLock = UnitLock({ it.unitType == type.whatBuilds().first || it.buildType == type}) { Styx.resources.availableUnits.firstOrNull { it.unitType == type.whatBuilds().first } }
    private val costLock = UnitCostLock(type)

    override fun performTick(): NodeStatus {
        if (trainerLock.unit?.unitType == type)
            return NodeStatus.DONE
        buildPlan.pendingUnits += type
        if (trainerLock.unit?.buildType == type)
            return NodeStatus.RUNNING
        trainerLock.acquire()
        costLock.acquire()
        if (!costLock.satisfied)
            return NodeStatus.RUNNING
        val trainer = trainerLock.unit ?: return NodeStatus.RUNNING
        BasicActions.train(trainer, type)
        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        trainerLock.reset()
    }
}

class Get(private val amount: Int, val type: UnitType) : MemoLeaf() {
    private val children = ArrayDeque<BTNode>()

    override fun performTick(): NodeStatus {
        val remaining = max(0, amount - Styx.units.my(type).size)
        if (remaining == 0)
            return NodeStatus.DONE
        val expectedChildCount = if (type.isTwoUnitsInOneEgg) (remaining + 1) / 2 else remaining
        repeat(expectedChildCount - children.size) {
            children += if (type.isBuilding) Build(type) else Train(type)
        }
        repeat(children.size - expectedChildCount) {
            children.removeFirst()
        }
        return tickPar(children.asSequence().map { it.tick() })
    }
}

class Upgrade(private val upgrade: UpgradeType, private val level: Int) : MemoLeaf() {
    private val costLock = UpgradeCostLock(upgrade, level)
    private val researcherLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == upgrade.whatUpgrades() } }

    override fun performTick(): NodeStatus {
        if (Styx.self.getUpgradeLevel(upgrade) == level) return NodeStatus.DONE
        costLock.acquire()
        if (costLock.satisfied) {
            researcherLock.acquire()
            val researcher = researcherLock.unit ?: return NodeStatus.RUNNING
            researcher.upgrade(upgrade)
        }
        return NodeStatus.RUNNING
    }
}


class Research(private val tech: TechType) : MemoLeaf() {
    private val costLock = TechCostLock(tech)
    private val researcherLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == tech.whatResearches() } }

    override fun performTick(): NodeStatus {
        if (Styx.self.hasResearched(tech)) return NodeStatus.DONE
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