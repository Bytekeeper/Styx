package org.fttbot

enum class NodeStatus {
    FAILED,
    SUCCEEDED,
    RUNNING
}

interface Node {
    fun tick(): NodeStatus
    fun aborted() {}
    infix fun onlyIf(condition: Node): Node = object : Node {
        override fun tick(): NodeStatus =
                if (condition.tick() == NodeStatus.SUCCEEDED)
                    this@Node.tick()
                else NodeStatus.FAILED

        override fun toString(): String = "${this@Node} only if ${condition}"
    }
}

class Then(val delegate: () -> Node) : Node {
    private val actualDelegate: Node by lazy { delegate() }
    override fun tick(): NodeStatus = actualDelegate.tick()
}

class Select(val delegate: () -> NodeStatus) : Node {
    override fun tick(): NodeStatus = delegate()
}

class All<E>(val children: List<Node>) : Node {
    constructor(vararg children: Node) : this(children.toList())

    override fun tick(): NodeStatus {
        val result = children.map { it.tick() }
        if (result.any { it == NodeStatus.FAILED }) return NodeStatus.FAILED
        if (result.any { it == NodeStatus.RUNNING }) return NodeStatus.RUNNING
        return NodeStatus.SUCCEEDED
    }
}

class AtLeastOne(vararg val childArray: Node) : Node {
    val children = childArray.toMutableList()
    override fun tick(): NodeStatus {
        val result = children.map { it to it.tick() }
        children.removeAll(result.filter { it.second != NodeStatus.RUNNING }.map { it.first }.toList())
        if (result.any { it.second == NodeStatus.SUCCEEDED }) return NodeStatus.SUCCEEDED
        if (result.any { it.second == NodeStatus.RUNNING }) return NodeStatus.RUNNING
        return NodeStatus.FAILED
    }
}

class Multiplex<T : Any>(val childrenResolver: () -> Collection<T>, val nodeGen: (T) -> Node) : Node {
    val children = HashMap<T, Node>()
    override fun tick(): NodeStatus {
        val subBoards = childrenResolver()
        val toDelete = subBoards.mapNotNull {
            val result = children.computeIfAbsent(it, nodeGen)
                    .tick()
            if (result != NodeStatus.RUNNING)
                it
            else
                null
        }
        children.keys.removeAll(toDelete)
        children.keys.removeIf { !subBoards.contains(it) }
        return if (children.isEmpty()) return NodeStatus.SUCCEEDED else NodeStatus.RUNNING
    }
}

class ToNodes<T : Any>(val childrenResolver: () -> Collection<T>, val nodeGen: (T) -> Node) : Node {
    val children = ArrayList<Node>()
    override fun tick(): NodeStatus {
        val newItems = childrenResolver()
        children.addAll(newItems.map(nodeGen))

        val toDelete = children.mapNotNull {
            val result = it.tick()
            if (result != NodeStatus.RUNNING)
                it
            else
                null
        }
        children.removeAll(toDelete)
        return if (children.isEmpty()) return NodeStatus.SUCCEEDED else NodeStatus.RUNNING
    }
}


class Loop<T : Any>(val nextItem: () -> T?, val nodeGen: (T) -> Node) : Node {
    var currentNode: Node? = null

    override fun tick(): NodeStatus {
        while (true) {
            val toProcess = currentNode ?: nextItem()?.let { nodeGen(it) } ?: return NodeStatus.SUCCEEDED

            when (toProcess.tick()) {
                NodeStatus.FAILED -> return NodeStatus.FAILED
                NodeStatus.RUNNING -> {
                    currentNode = toProcess
                    return NodeStatus.RUNNING
                }
                else -> currentNode = null
            }
        }
    }

    override fun aborted() {
        currentNode?.aborted()
        currentNode = null
    }
}

infix fun <T : Node> T.by(utility: () -> Double) = UtilityTask(this) { utility() }

class BTree(val root: Node) {
    fun tick(): NodeStatus = root.tick()
}

class Repeat(val node: Node, val times: Int = 20) : Node {
    override fun tick(): NodeStatus {
        for (i in 1..times) {
            val result = node.tick()
            if (result != NodeStatus.SUCCEEDED) {
                return result
            }
        }
        return NodeStatus.FAILED
    }
}

class UtilityFallback(vararg val childArray: UtilityTask, val taskProviders: List<() -> List<UtilityTask>> = emptyList()) : FallbackBase() {
    override fun children(): List<Node> =
            (childArray.toList() + taskProviders.flatMap { it() }).sortedByDescending { it.utility() }
}

class UtilitySequence(vararg val childArray: UtilityTask, val taskProviders: List<() -> List<UtilityTask>> = emptyList()) : SequenceBase() {
    override fun children(): List<Node> =
            (childArray.toList() + taskProviders.flatMap { it() }).sortedByDescending { it.utility() }
}

class UtilityTask(val wrappedTask: Node, val utility: () -> Double) : Node {
    override fun tick(): NodeStatus = wrappedTask.tick()
}

object Success : Node {
    override fun tick(): NodeStatus = NodeStatus.SUCCEEDED
    override fun toString(): String = "SUCCESS"
}

object Fail : Node {
    override fun tick(): NodeStatus = NodeStatus.FAILED
    override fun toString(): String = "FAIL"
}

object Sleep : Node {
    override fun tick(): NodeStatus = NodeStatus.RUNNING
    override fun toString(): String = "RUNNING"
}

abstract class FallbackBase : Node {
    private var lastRunningChild: Node? = null
    abstract fun children(): List<Node>

    override fun tick(): NodeStatus {
        children().forEach {
            val result = it.tick()
            if (result != NodeStatus.FAILED) {
                if (lastRunningChild != it && lastRunningChild != null) {
                    lastRunningChild!!.aborted()
                }
                if (result == NodeStatus.RUNNING) {
                    lastRunningChild = it
                } else {
                    lastRunningChild = null
                }
                return result
            }
            if (lastRunningChild == it) {
                lastRunningChild = null
            }
        }
        return NodeStatus.FAILED
    }

    override fun aborted() {
        lastRunningChild?.aborted()
        lastRunningChild = null
    }

}

open class Fallback(vararg childArray: Node) : FallbackBase() {
    private val _children: List<Node> = childArray.toList()

    override fun children(): List<Node> = _children

    override fun toString(): String = "Fallback(${_children.size})"
}

abstract class SequenceBase : Node {
    private var lastRunningChild: Node? = null
    abstract fun children(): List<Node>

    override fun tick(): NodeStatus {
        children().forEach {
            val result = it.tick()
            if (result != NodeStatus.SUCCEEDED) {
                if (lastRunningChild != it && lastRunningChild != null) {
                    lastRunningChild!!.aborted()
                }
                if (result == NodeStatus.RUNNING) {
                    lastRunningChild = it
                } else {
                    lastRunningChild = null
                }
                return result
            }
            if (lastRunningChild == it) {
                lastRunningChild = null
            }
        }
        return NodeStatus.SUCCEEDED
    }

    override fun aborted() {
        lastRunningChild?.aborted()
        lastRunningChild = null
    }
}

class Sequence(vararg childArray: Node) : SequenceBase() {
    private val _children: List<Node> = childArray.toList()

    override fun children(): List<Node> = _children

    override fun toString(): String = "Sequence(${_children.size})"
}

class MSequence(vararg val children: Node) : Node {
    var childIndex = -1

    override fun tick(): NodeStatus {
        if (childIndex < 0) childIndex = 0
        do {
            val result = children[childIndex].tick()
            if (result == NodeStatus.FAILED) {
                childIndex = -1
                return NodeStatus.FAILED
            }
            if (result == NodeStatus.RUNNING) {
                return NodeStatus.RUNNING
            }
        } while (++childIndex < children.size)
        childIndex = -1
        return NodeStatus.SUCCEEDED
    }

    override fun aborted() {
        if (childIndex >= 0) {
            children[childIndex].aborted()
        }
        childIndex = -1
    }
}

class MSelector<E>(vararg val children: Node) : Node {
    var childIndex = -1

    override fun tick(): NodeStatus {
        if (childIndex < 0) childIndex = 0
        do {
            val result = children[childIndex].tick()
            if (result == NodeStatus.SUCCEEDED) {
                childIndex = -1
                return NodeStatus.SUCCEEDED
            }
            if (result == NodeStatus.RUNNING) {
                return NodeStatus.RUNNING
            }
        } while (++childIndex < children.size)
        childIndex = -1
        return NodeStatus.FAILED
    }

    override fun aborted() {
        if (childIndex >= 0) {
            children[childIndex].aborted()
        }
        childIndex = -1
    }
}