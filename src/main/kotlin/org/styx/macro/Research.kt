package org.styx.macro

import bwapi.TechType
import org.bk.ass.bt.NodeStatus
import org.styx.*

class Research(private val tech: TechType) : MemoLeaf() {
    private val costLock = costLocks.techCostLock(tech)
    private val researcherLock = UnitLock() { UnitReservation.availableItems.firstOrNull { it.unitType == tech.whatResearches() } }

    override fun tick(): NodeStatus {
        if (Styx.self.hasResearched(tech))
            return NodeStatus.SUCCESS
        if (Styx.self.isResearching(tech))
            return NodeStatus.RUNNING;
        costLock.acquire()
        if (costLock.isSatisfied) {
            researcherLock.acquire()
            val researcher = researcherLock.item ?: return NodeStatus.RUNNING
            researcher.research(tech)
        }
        return NodeStatus.RUNNING
    }

}