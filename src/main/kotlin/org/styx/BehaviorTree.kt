package org.styx

enum class TickResult {
    RUNNING,
    DONE,
    FAILED
}

abstract class BTNode {
    var tree: BehaviorTree? = null
        private set
    fun <T> board() = tree!!.board<T>()
    open val priority: Double = 0.0
    abstract fun tick(): TickResult
    open fun setTree(tree: BehaviorTree) {
        this.tree = tree
    }

    open fun reset() {}

    companion object {
        fun tickPar(childResults: Sequence<TickResult>): TickResult {
            val results = childResults.takeWhile { it != TickResult.FAILED }.toList()
            if (results.last() == TickResult.FAILED)
                return TickResult.FAILED
            if (results.any { it == TickResult.RUNNING })
                return TickResult.RUNNING
            return TickResult.DONE
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
    final override fun tick(): TickResult {
        val result = performTick()
        if (result != TickResult.RUNNING) reset()
        return result
    }

    abstract fun performTick(): TickResult
}

open class Sel(name: String, vararg children: BTNode) : CompoundNode(name, *children) {
    override fun performTick(): TickResult =
            tickedChilds.firstOrNull { it != TickResult.FAILED }
                    ?: TickResult.FAILED
}

open class Seq(name: String, vararg children: BTNode) : CompoundNode(name, *children) {
    override fun performTick(): TickResult = tickedChilds
            .takeWhile { it == TickResult.DONE }.lastOrNull()
            ?: TickResult.DONE
}

open class Par(name: String, vararg children: BTNode) : CompoundNode(name, *children) {
    override fun performTick(): TickResult = BTNode.tickPar(tickedChilds)
}

class Memo(private val delegate: BTNode) : BTNode() {
    var result: TickResult? = null

    override fun tick(): TickResult = result ?: run {
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
    var result: TickResult? = null

    override fun tick(): TickResult = result ?: run {
        val childResult = performTick()
        if (childResult != TickResult.RUNNING) result = childResult
        childResult
    }

    override fun reset() {
        result = null
    }

    abstract fun performTick(): TickResult
}

class Condition(private val condition: () -> Boolean) : BTNode() {
    override fun tick(): TickResult = if (condition()) TickResult.DONE else TickResult.RUNNING
}

class Repeat(private val amount: Int = -1, private val delegate: BTNode) : BTNode() {
    private var remaining = amount

    override fun tick(): TickResult {
        if (remaining == 0) return TickResult.DONE
        val result = delegate.tick()
        if (result != TickResult.DONE) return result
        if (remaining > 0) {
            remaining--
            if (remaining == 0) return TickResult.DONE
        }
        delegate.reset()
        return TickResult.RUNNING
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

    override fun tick(): TickResult {
        val list = source()
        currentTrees.keys.retainAll(list)
        list.forEach { s ->
            val result = currentTrees.computeIfAbsent(s, treeProducer).tick()
            if (result != TickResult.RUNNING) currentTrees.remove(s)
        }
        return TickResult.RUNNING
    }

    override fun reset() {
        super.reset()
        currentTrees.values.forEach { it.reset() }
    }
}
