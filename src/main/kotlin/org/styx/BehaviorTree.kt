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

val Wait = org.bk.ass.bt.Wait.INSTANCE

typealias NodeFactory = () -> TreeNode

fun NodeFactory.repeat(times: Int) =
    Parallel(*(0..times).map { this() }.toTypedArray())
