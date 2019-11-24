package org.styx.macro

import bwapi.TechType
import org.styx.*

class Research(private val tech: TechType) : MemoLeaf() {
    private val costLock = TechCostLock(tech)
    private val researcherLock = UnitLock() { Styx.resources.availableUnits.firstOrNull { it.unitType == tech.whatResearches() } }

    override fun tick(): NodeStatus {
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