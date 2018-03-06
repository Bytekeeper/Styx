package org.fttbot

import org.apache.logging.log4j.LogManager
import kotlin.Any

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

class MDelegate(val name: String? = "", val delegate: () -> Node) : Node {
    private var actualDelegate: Node? = null
    override fun tick(): NodeStatus {
        if (actualDelegate == null) {
            actualDelegate = delegate()
        }
        var result = actualDelegate!!.tick()
        if (result != NodeStatus.RUNNING) {
            actualDelegate = null
        }
        return result
    }

    override fun aborted() {
        actualDelegate = null
    }

    override fun toString(): String = "$name@mdel"
}

class Delegate(val delegate: () -> Node) : Node {
    override fun tick(): NodeStatus = delegate().tick()
}

class Inline(val delegate: () -> NodeStatus) : Node {
    override fun tick(): NodeStatus = delegate()
}


class Await(var frames: Int = Int.MAX_VALUE, val condition: () -> Boolean) : Node {
    override fun tick(): NodeStatus {
        frames--
        if (frames <= 0) return NodeStatus.FAILED
        return if (condition()) NodeStatus.SUCCEEDED else NodeStatus.RUNNING
    }
}

class Require(val condition: () -> Boolean) : Node {
    override fun tick(): NodeStatus = if (condition()) NodeStatus.SUCCEEDED else NodeStatus.FAILED
}

class Retry(var times: Int, val node: Node) : Node {
    override fun tick(): NodeStatus {
        if (times > 0) {
            var result = node.tick()
            if (result == NodeStatus.FAILED) {
                times--
                node.aborted()
                return NodeStatus.RUNNING
            }
            return result
        }
        return NodeStatus.FAILED
    }

}

class All(val name : String, vararg val children: Node) : Node {
    override fun tick(): NodeStatus {
        var result = children.map { it.tick() }
        if (result.contains(NodeStatus.FAILED)) return NodeStatus.FAILED
        if (result.contains(NodeStatus.RUNNING)) return NodeStatus.RUNNING
        return NodeStatus.SUCCEEDED
    }

    override fun toString(): String = "$name@all(${children.joinToString(",")})"
}

class MAll(var children: MutableList<Node>) : Node {
    constructor(vararg children: Node) : this(children.toMutableList())

    override fun tick(): NodeStatus {
        var abort = false
        var overallResult: NodeStatus = NodeStatus.SUCCEEDED
        val remaining = children.mapNotNull {
            if (abort) {
                it.aborted()
                it
            } else {
                val result = it.tick()
                if (result == NodeStatus.FAILED) {
                    abort = true
                    overallResult = result
                    null
                } else if (result == NodeStatus.RUNNING) {
                    overallResult = NodeStatus.RUNNING
                    it
                } else {
                    null
                }
            }
        }
        children = remaining.toMutableList()
        return overallResult
    }
}

class OneOf(vararg val children: Node) : Node {
    override fun tick(): NodeStatus {
        var abort = false
        var overallResult: NodeStatus = NodeStatus.RUNNING
        children.forEach {
            if (abort) {
                it.aborted()
            } else {
                val result = it.tick()
                if (result != NodeStatus.RUNNING) {
                    abort = true
                    overallResult = result
                }
            }
        }
        return overallResult
    }

    override fun toString(): String = "one(${children.joinToString(", ")})"
}

class MMapAll<T : Any>(val childrenResolver: () -> Collection<T>, val name: String? = "", val nodeGen: (T) -> Node) : Node {
    private val LOG = LogManager.getLogger()
    private val children = HashMap<T, Node>()
    override fun tick(): NodeStatus {
        val toTransform = childrenResolver()
        val result = toTransform.map {
            val result = children.computeIfAbsent(it, nodeGen)
                    .tick()
            if (result == NodeStatus.SUCCEEDED) {
                children.remove(it)
            }
            result
        }
        children.keys.removeIf { !toTransform.contains(it) }
        return if (result.contains(NodeStatus.FAILED))
            return NodeStatus.FAILED
        else if (result.contains(NodeStatus.RUNNING))
            NodeStatus.RUNNING
        else NodeStatus.SUCCEEDED
    }

    override fun toString(): String = "$name@mplex(${children.values.joinToString(", ")})"
}

class ToNodes<T : Any>(val childrenResolver: () -> Collection<T>, val nodeGen: (T) -> Node) : Node {
    private val LOG = LogManager.getLogger()
    private val children = ArrayList<Node>()
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
        if (LOG.isInfoEnabled) {
            toDelete.forEach { LOG.info("Removed $it") }
        }
        children.removeAll(toDelete)
        return if (children.isEmpty()) return NodeStatus.SUCCEEDED else NodeStatus.RUNNING
    }
}


infix fun <T : Node> T.by(utility: () -> Double) = UtilityTask(this) { utility() }

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

class Sleep(var timeOut: Int = Int.MAX_VALUE, val condition: () -> Boolean = { true }) : Node {
    override fun tick(): NodeStatus =
            if (--timeOut <= 0) NodeStatus.FAILED
            else if (condition()) NodeStatus.RUNNING
            else NodeStatus.SUCCEEDED

    override fun toString(): String = "RUNNING while $condition"

    companion object : Node {
        override fun tick(): NodeStatus = NodeStatus.RUNNING
        override fun toString(): String = "RUNNING"
    }
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

    override fun toString(): String = "fback(${children().joinToString(", ")})"
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

    override fun toString(): String = "seq(${children().joinToString(", ")})"
}

class Sequence(vararg childArray: Node) : SequenceBase() {
    private val _children: List<Node> = childArray.toList()

    override fun children(): List<Node> = _children

    override fun toString(): String = "Sequence(${_children.joinToString(",")})"
}

class MSequence(val name: String, vararg val children: Node) : Node {
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

    override fun toString(): String = "$name@MSequence@$childIndex(${children.joinToString(",")})"
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