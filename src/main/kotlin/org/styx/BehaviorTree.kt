package org.styx

import org.styx.Styx.ft

enum class NodeStatus {
    INITIAL,
    RUNNING,
    DONE,
    FAILED;

    fun after(code: () -> Unit): () -> NodeStatus = {
        code()
        this
    }
}

typealias SimpleNode = () -> NodeStatus

interface Resettable {
    fun reset()
}

interface Prioritized {
    val priority: Double
}

interface Learner {
    fun loadLearned()
    fun saveLearned() {}
}

abstract class BehaviorTree(val name: String) : SimpleNode, Resettable, Learner {
    abstract val root: SimpleNode
    var status: NodeStatus = NodeStatus.INITIAL
        private set

    override fun invoke(): NodeStatus {
        status = root()
        return status
    }

    override fun reset() {
        status = NodeStatus.INITIAL
        (root as? Resettable)?.reset()
    }

    override fun loadLearned() {
        (root as? Learner)?.loadLearned()
    }

    override fun saveLearned() {
        (root as? Learner)?.saveLearned()
    }

    override fun toString(): String = name
}

abstract class BTNode : SimpleNode, Prioritized, Learner, Resettable {
    var status: NodeStatus = NodeStatus.INITIAL
        private set
    override val priority: Double = 0.0
    protected abstract fun tick(): NodeStatus

    override fun invoke(): NodeStatus {
        status = tick()
        return status
    }

    override fun reset() {
        status = NodeStatus.INITIAL
    }

    override fun loadLearned() {
    }

    override fun saveLearned() {
    }
}

class Dispatch<T>(
        name: String,
        private val valuesProvider: () -> Collection<T>,
        private val treeMaker: (T) -> SimpleNode) : CompoundNode(name) {
    private val managed = mutableMapOf<T, SimpleNode>()

    override val children: Collection<SimpleNode>
        get() {
            val values = valuesProvider()
            managed.keys.retainAll(values)
            return values.map { value ->
                managed.computeIfAbsent(value) {
                    treeMaker(it)
                }
            }
        }

    override fun tick(): NodeStatus {
        val results = tickedChilds.toList()
        if (results.contains(NodeStatus.FAILED))
            return NodeStatus.FAILED
        if (results.contains(NodeStatus.RUNNING))
            return NodeStatus.RUNNING
        return NodeStatus.DONE
    }
}

abstract class CompoundNode(val name: String) : BTNode() {
    protected abstract val children: Collection<SimpleNode>
    protected open val tickedChilds
        get() = children.asSequence().map { ft(name, it) }

    override fun reset() = children.filterIsInstance<Resettable>().forEach(Resettable::reset)

    override fun loadLearned() {
        children.filterIsInstance<Learner>().forEach(Learner::loadLearned)
    }

    override fun saveLearned() {
        children.filterIsInstance<Learner>().forEach(Learner::saveLearned)
    }
}

class Best(name: String, vararg children: SimpleNode) : CompoundNode(name) {
    override val children = children.toList()
    override fun tick(): NodeStatus = children.maxBy {
        (it as? Prioritized)?.priority ?: 0.0
    }?.let { ft(name, it) } ?: NodeStatus.FAILED

    override fun toString(): String = "Best $name"
}

open class Sel(name: String, vararg children: SimpleNode) : CompoundNode(name) {
    override val children: Collection<SimpleNode> = children.toList()
    override val tickedChilds
        get() = children.sortedByDescending {
            (it as? Prioritized)?.priority ?: 0.0
        }.asSequence().map { ft(name, it) }

    override fun tick(): NodeStatus =
            tickedChilds.firstOrNull { it != NodeStatus.FAILED }
                    ?: NodeStatus.FAILED

    override fun toString(): String = "Sel $name"
}

class Seq(name: String, vararg children: SimpleNode) : CompoundNode(name) {
    override val children: Collection<SimpleNode> = children.asList()

    override fun tick(): NodeStatus = tickedChilds
            .firstOrNull { it != NodeStatus.DONE }
            ?: NodeStatus.DONE

    override fun toString(): String = "Seq $name"
}

class Par(name: String, private val continueOnFail: Boolean = false, vararg children: SimpleNode) : CompoundNode(name) {
    override val children: Collection<SimpleNode> = children.asList()

    override fun tick(): NodeStatus {
        val results = tickedChilds
                .takeWhile { it != NodeStatus.FAILED || continueOnFail }
                .toList()
        if (results.contains(NodeStatus.FAILED))
            return NodeStatus.FAILED
        if (results.contains(NodeStatus.RUNNING))
            return NodeStatus.RUNNING
        return NodeStatus.DONE
    }

    companion object {
        fun repeat(name: String, amount: Int, childSupplier: () -> BehaviorTree) =
                Par(name, false, *(0 until amount).map { childSupplier() }.toTypedArray())
    }

    override fun toString(): String = "Par $name"
}

open class Memo(private val delegate: BTNode) : BTNode() {
    override fun tick(): NodeStatus {
        if (status != NodeStatus.INITIAL && status != NodeStatus.RUNNING)
            return status
        return delegate()
    }

    override fun reset() {
        super.reset()
        delegate.reset()
    }
}

abstract class MemoLeaf : BTNode() {
    override fun invoke(): NodeStatus {
        if (status != NodeStatus.INITIAL && status != NodeStatus.RUNNING)
            return status
        return super.invoke()
    }
}

class WaitFor(private val condition: () -> Boolean) : BTNode() {
    override fun tick(): NodeStatus =
            if (condition()) NodeStatus.DONE else NodeStatus.RUNNING
}

class Condition(private val condition: () -> Boolean) : BTNode() {
    override fun tick(): NodeStatus =
            if (condition()) NodeStatus.DONE else NodeStatus.FAILED
}

class Repeat(private val amount: Int = -1, private val repeatOnFailure: Boolean = true, private val delegate: BTNode) : BTNode() {
    private var remaining = amount

    override fun tick(): NodeStatus {
        if (remaining == 0) {
            return NodeStatus.DONE
        }
        delegate()
        if (delegate.status != NodeStatus.DONE) {
            if (repeatOnFailure && delegate.status == NodeStatus.FAILED) {
                delegate.reset()
            }
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
