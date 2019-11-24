package org.styx.macro

import org.styx.BTNode
import org.styx.NodeStatus
import org.styx.SUnit

class Cancel(val unit: SUnit) : BTNode() {
    override fun tick(): NodeStatus {
        if (!unit.exists )
            return NodeStatus.DONE
        if (unit.morphing)
            unit.cancelMorph()
        return NodeStatus.RUNNING
    }
}