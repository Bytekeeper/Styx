package org.styx

import org.styx.Styx.ft

enum class NodeStatus {
    INITIIAL,
    RUNNING,
    DONE,
    FAILED
}


abstract class BTNode {
    var status: NodeStatus = NodeStatus.INITIIAL
        private set
    open val priority: Double = 0.0
    protected abstract fun tick(): NodeStatus

    open fun perform(): NodeStatus {
        status = tick()
        return status
    }

    open fun reset() {
        status = NodeStatus.INITIIAL
    }

    companion object {
        fun tickPar(childResults: Sequence<NodeStatus>): NodeStatus {
            val results = childResults.takeWhile { it != NodeStatus.FAILED }.toList()
            val last = results.lastOrNull() ?: return NodeStatus.DONE
            if (last == NodeStatus.FAILED)
                return NodeStatus.FAILED
            if (results.any { it == NodeStatus.RUNNING })
                return NodeStatus.RUNNING
            return NodeStatus.DONE
        }
    }
}

abstract class CompoundNode(val name: String) : BTNode() {
    protected abstract val children : Collection<BTNode>
    protected val tickedChilds get() = children.sortedByDescending { it.priority }.asSequence().map { ft(name, it::perform) }
    override fun reset() = children.forEach { it.reset() }
}

open class Sel(name: String, vararg children: BTNode) : CompoundNode(name) {
    override val children: List<BTNode> = children.toList()
    override fun tick(): NodeStatus =
            tickedChilds.firstOrNull { it != NodeStatus.FAILED }
                    ?: NodeStatus.FAILED

    override fun toString(): String = "Sel $name"
}

open class Seq(name: String, vararg children: BTNode) : CompoundNode(name) {
    override val children: List<BTNode> = children.toList()
    override fun tick(): NodeStatus = tickedChilds
            .takeWhile { it == NodeStatus.DONE }.lastOrNull()
            ?: NodeStatus.DONE

    override fun toString(): String = "Seq $name"
}

open class Par(name: String, vararg children: BTNode) : CompoundNode(name) {
    override val children: Collection<BTNode> = children.toList()
    override fun tick(): NodeStatus = tickPar(tickedChilds)

    companion object {
        fun repeat(name: String, amount: Int, childSupplier: () -> BTNode) =
                Par(name, *(0 until amount).map { childSupplier() }.toTypedArray())
    }

    override fun toString(): String = "Par $name"
}

open class Memo(private val delegate: BTNode) : BTNode() {
    override fun tick(): NodeStatus {
        if (status != NodeStatus.INITIIAL && status != NodeStatus.RUNNING)
            return status
        return delegate.perform()
    }

    override fun reset() {
        super.reset()
        delegate.reset()
    }
}

abstract class MemoLeaf : BTNode() {
    final override fun perform(): NodeStatus {
        if (status != NodeStatus.INITIIAL && status != NodeStatus.RUNNING)
            return status
        return super.perform()
    }
}

class Condition(private val condition: () -> Boolean) : BTNode() {
    override fun tick(): NodeStatus =
            if (condition()) NodeStatus.DONE else NodeStatus.RUNNING
}

class Repeat(private val amount: Int = -1, private val delegate: BTNode) : BTNode() {
    private var remaining = amount

    override fun tick(): NodeStatus {
        if (remaining == 0) {
            return NodeStatus.DONE
        }
        delegate.perform()
        if (delegate.status != NodeStatus.DONE) {
            return delegate.status
        }
        if (remaining > 0) {
            remaining--
            if (remaining == 0) {
                return NodeStatus.DONE
            }
        }
        delegate.reset()
        return NodeStatus.RUNNING
    }

    override fun reset() {
        remaining = amount
        delegate.reset()
    }

    override fun toString(): String = "Repeat $amount times: $delegate"
}
