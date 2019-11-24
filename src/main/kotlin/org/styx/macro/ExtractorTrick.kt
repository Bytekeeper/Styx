package org.styx.macro

import bwapi.UnitType
import org.styx.MemoLeaf
import org.styx.NodeStatus
import org.styx.UnitCostLock

class ExtractorTrick() : MemoLeaf() {
    private var build = StartBuild(UnitType.Zerg_Extractor)
    private val train = Train(UnitType.Zerg_Drone)
    private var cancel: Cancel? = null
    private val costLock = UnitCostLock(UnitType.Zerg_Drone)

    override fun tick(): NodeStatus {
        costLock.acquire()
        if (build.perform() == NodeStatus.FAILED)
            return NodeStatus.FAILED
        costLock.release()
        val extractor = build.building ?: return NodeStatus.RUNNING
        cancel = cancel ?: Cancel(extractor)
        train.perform()
        if (train.status == NodeStatus.FAILED) {
            if (cancel!!.perform() != NodeStatus.RUNNING)
                return NodeStatus.FAILED
            return NodeStatus.RUNNING
        }
        if (train.trainedUnit != null) {
            return cancel!!.perform()
        }
        return NodeStatus.RUNNING
    }
}