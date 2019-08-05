package org.styx.macro

import bwapi.TechType
import bwapi.TilePosition
import bwapi.UnitType
import bwapi.UpgradeType
import org.styx.*
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

    override fun performTick(): TickResult {
        at?.let {
            val targetPos = it.toPosition() + type.dimensions / 2
            val candidate = Styx.units.my(type).closestTo(targetPos.x, targetPos.y).orNull()
            if (candidate?.tilePosition == it)
                return TickResult.DONE
            if (!game.canBuildHere(it, type, workerLock.unit?.unit))
                at = null
        }
        val buildAt = at ?: ConstructionPosition.findPositionFor(type) ?: return TickResult.RUNNING
        at = buildAt
        costLock.acquire()
        workerLock.acquire()
        val worker = workerLock.unit ?: return TickResult.RUNNING
        if (costLock.willBeSatisfied)
            BasicActions.build(worker, type, buildAt)
        else
            workerLock.release()
        return TickResult.RUNNING
    }

    override fun reset() {
        super.reset()
        workerLock.reset()
    }
}

/**
 * Trigger training of unit, don't wait for completion
 */
class Train(private val type: UnitType) : MemoLeaf() {
    private val trainerLock = UnitLock { Styx.resources.availableUnits.firstOrNull { it.unitType == type.whatBuilds().first } }
    private val costLock = UnitCostLock(type)

    override fun performTick(): TickResult {
        if (trainerLock.unit?.unitType == type)
            return TickResult.DONE
        trainerLock.acquire()
        costLock.acquire()
        if (!costLock.satisfied)
            return TickResult.RUNNING
        val trainer = trainerLock.unit ?: return TickResult.RUNNING
        if (trainer.unitType == type) return TickResult.DONE
        BasicActions.train(trainer, type)
        return TickResult.RUNNING
    }

    override fun reset() {
        super.reset()
        trainerLock.reset()
    }
}

class Get(private val amount: Int, val type: UnitType) : MemoLeaf() {
    private val children = ArrayDeque<BTNode>()

    override fun performTick(): TickResult {
        val remaining = max(0, amount - Styx.units.my(type).size)
        if (remaining == 0)
            return TickResult.DONE
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

    override fun performTick(): TickResult {
        if (Styx.self.getUpgradeLevel(upgrade) == level) return TickResult.DONE
        costLock.acquire()
        if (costLock.satisfied) {
            researcherLock.acquire()
            val researcher = researcherLock.unit ?: return TickResult.RUNNING
            researcher.upgrade(upgrade)
        }
        return TickResult.RUNNING
    }
}


class Research(private val tech: TechType) : MemoLeaf() {
    private val costLock = TechCostLock(tech)
    private val researcherLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == tech.whatResearches() } }

    override fun performTick(): TickResult {
        if (Styx.self.hasResearched(tech)) return TickResult.DONE
        costLock.acquire()
        if (costLock.satisfied) {
            researcherLock.acquire()
            val researcher = researcherLock.unit ?: return TickResult.RUNNING
            researcher.research(tech)
        }
        return TickResult.RUNNING
    }

}
