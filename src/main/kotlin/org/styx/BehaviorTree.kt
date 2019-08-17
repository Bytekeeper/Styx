package org.styx

enum class NodeStatus {
    RUNNING,
    DONE,
    FAILED
}

abstract class BTNode {
    var tree: BehaviorTree? = null
        private set
    fun <T> board() = tree!!.board<T>()
    open val priority: Double = 0.0
    abstract fun tick(): NodeStatus
    open fun setTree(tree: BehaviorTree) {
        this.tree = tree
    }

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

class BehaviorTree(private val root: BTNode, private val board: Any? = null) {
    init {
        root.setTree(this)
    }

    fun tick() = root.tick()

    fun reset() = root.reset()

    fun <T> board() = board as T
}

abstract class CompoundNode(val name: String, private vararg val children: BTNode) : BTNode() {
    protected val tickedChilds get() = children.sortedByDescending { it.priority }.asSequence().map { it.tick() }
    override fun setTree(tree: BehaviorTree) = children.forEach { it.setTree((tree)) }
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

    override fun setTree(tree: BehaviorTree) {
        delegate.setTree(tree)
    }
}

class ChildTrees<T>(private val source: () -> List<T>, private val treeProducer: (T) -> BehaviorTree) : BTNode() {
    private val currentTrees = mutableMapOf<T, BehaviorTree>()

    override fun tick(): NodeStatus {
        val list = source()
        currentTrees.keys.retainAll(list)
        list.forEach { s ->
            val result = currentTrees.computeIfAbsent(s, treeProducer).tick()
            if (result != NodeStatus.RUNNING) currentTrees.remove(s)
        }
        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        currentTrees.values.forEach { it.reset() }
    }
}
