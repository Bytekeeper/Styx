package org.styx

enum class NodeStatus {
    RUNNING,
    DONE,
    FAILED
}

abstract class BTNode {
    open val priority: Double = 0.0
    abstract fun tick(): NodeStatus

    open fun reset() {}

    companion object {
        fun tickPar(childResults: Sequence<NodeStatus>): NodeStatus {
            val results = childResults.takeWhile { it != NodeStatus.FAILED }.toList()
            if (results.last() == NodeStatus.FAILED)
                return NodeStatus.FAILED
            if (results.any { it == NodeStatus.RUNNING })
                return NodeStatus.RUNNING
            return NodeStatus.DONE
        }
    }
}

abstract class CompoundNode(val name: String, private vararg val children: BTNode) : BTNode() {
    protected val tickedChilds get() = children.sortedByDescending { it.priority }.asSequence().map { it.tick() }
    override fun reset() = children.forEach { it.reset() }
    final override fun tick(): NodeStatus {
        val result = performTick()
        if (result != NodeStatus.RUNNING) reset()
        return result
    }

    abstract fun performTick(): NodeStatus
}

open class Sel(name: String, vararg children: BTNode) : CompoundNode(name, *children) {
    override fun performTick(): NodeStatus =
            tickedChilds.firstOrNull { it != NodeStatus.FAILED }
                    ?: NodeStatus.FAILED
}

open class Seq(name: String, vararg children: BTNode) : CompoundNode(name, *children) {
    override fun performTick(): NodeStatus = tickedChilds
            .takeWhile { it == NodeStatus.DONE }.lastOrNull()
            ?: NodeStatus.DONE
}

open class Par(name: String, vararg children: BTNode) : CompoundNode(name, *children) {
    override fun performTick(): NodeStatus = BTNode.tickPar(tickedChilds)
}

class Memo(private val delegate: BTNode) : BTNode() {
    var result: NodeStatus? = null

    override fun tick(): NodeStatus = result ?: run {
        val childResult = delegate.tick()
        result = childResult
        childResult
    }

    override fun reset() {
        delegate.reset()
        result = null
    }
}

abstract class MemoLeaf : BTNode() {
    var result: NodeStatus? = null

    override fun tick(): NodeStatus = result ?: run {
        val childResult = performTick()
        if (childResult != NodeStatus.RUNNING) result = childResult
        childResult
    }

    override fun reset() {
        result = null
    }

    abstract fun performTick(): NodeStatus
}

class Condition(private val condition: () -> Boolean) : BTNode() {
    override fun tick(): NodeStatus = if (condition()) NodeStatus.DONE else NodeStatus.RUNNING
}

class Repeat(private val amount: Int = -1, private val delegate: BTNode) : BTNode() {
    private var remaining = amount

    override fun tick(): NodeStatus {
        if (remaining == 0) return NodeStatus.DONE
        val result = delegate.tick()
        if (result != NodeStatus.DONE) return result
        if (remaining > 0) {
            remaining--
            if (remaining == 0) return NodeStatus.DONE
        }
        delegate.reset()
        return NodeStatus.RUNNING
    }

    override fun reset() {
        remaining = amount
        delegate.reset()
    }
}

