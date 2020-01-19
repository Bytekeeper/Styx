package org.styx

import org.bk.ass.bt.NodeStatus
import org.bk.ass.bt.Parallel
import org.bk.ass.bt.TreeNode

abstract class MemoLeaf : TreeNode() {
    override fun exec() {
        if (status == NodeStatus.FAILURE || status == NodeStatus.SUCCESS)
            return
        val delegate = tick()
        if (delegate == NodeStatus.SUCCESS)
            success()
        else if (delegate == NodeStatus.FAILURE)
            failed()
        else
            running()
    }

    abstract fun tick(): NodeStatus
}

object Wait : TreeNode() {
    override fun init() {
        running()
    }

    override fun reset() {

    }

    override fun exec() {
    }
}
typealias NodeFactory = () -> TreeNode

fun NodeFactory.repeat(times: Int) =
    Parallel(Parallel.Policy.SEQUENCE, *(0..times).map { this() }.toTypedArray())
