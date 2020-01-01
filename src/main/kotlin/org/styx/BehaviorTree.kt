package org.styx

import org.bk.ass.bt.Decorator
import org.bk.ass.bt.NodeStatus
import org.bk.ass.bt.Parallel
import org.bk.ass.bt.TreeNode


class Dispatch<T>(
        private val valuesProvider: () -> Collection<T>,
        private val treeMaker: (T) -> TreeNode) : TreeNode() {
    private val managed = mutableMapOf<T, TreeNode>()

    private val children: Collection<TreeNode>
        get() {
            val values = valuesProvider()
            managed.keys.retainAll(values)
            return values.map { value ->
                managed.computeIfAbsent(value) {
                    val treeNode = treeMaker(it)
                    treeNode.init()
                    treeNode
                }
            }
        }

    override fun exec() {
        val results = children.map {
            it.exec()
            it.status
        }

        if (results.contains(NodeStatus.FAILURE))
            failed()
        else if (results.contains(NodeStatus.RUNNING))
            running()
        else
            success()
    }
}


abstract class MemoLeaf : TreeNode() {
    override fun exec() {
        if (status != NodeStatus.INITIAL && status != NodeStatus.RUNNING)
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

class DoneFilter(delegate: TreeNode) : Decorator(delegate) {
    override fun updateStatusFromDelegate(status: NodeStatus) {
        if (status == NodeStatus.SUCCESS)
            success()
        else
            failed()
    }
}

object Running : TreeNode() {
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
