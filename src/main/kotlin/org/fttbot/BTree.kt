package org.fttbot

import org.apache.logging.log4j.LogManager
import kotlin.math.max

enum class NodeStatus {
    FAILED,
    SUCCEEDED,
    RUNNING
}


interface Node {
    fun tick(): NodeStatus
    fun parentFinished() {}

    infix fun onlyIf(condition: Node): Node = object : Node {
        override fun tick(): NodeStatus =
                if (condition.tick() == NodeStatus.SUCCEEDED)
                    this@Node.tick()
                else NodeStatus.FAILED

        override fun toString(): String = "${this@Node} only if ${condition}"

        override fun parentFinished() {
            this@Node.parentFinished()
        }
    }
}

class Condition(val name: String, val condition: () -> Boolean) : Node {
    override fun tick(): NodeStatus = if (condition()) NodeStatus.SUCCEEDED else NodeStatus.FAILED
    override fun toString(): String = "require $name"
}

open class Decorator(val child: Node) : Node {
    override fun tick(): NodeStatus = child.tick()

    override fun parentFinished() {
        child.parentFinished()
    }
}

class Inline(val name: String, val delegate: () -> NodeStatus) : Node {
    override fun tick(): NodeStatus = delegate()

    override fun toString(): String = "Inline $name"
}

class Delegate(val subtreeProducer: () -> Node) : Node {
    var subtree: Node? = null;

    override fun tick(): NodeStatus {
        subtree = subtree ?: subtreeProducer()
        return subtree!!.tick()
    }

    override fun parentFinished() {
        subtree?.parentFinished()
        subtree = null
    }

}

class Await(val name: String, var toWait: Int = Int.MAX_VALUE, val condition: () -> Boolean) : Node {
    private var remaining = toWait
    override fun tick(): NodeStatus {
        if (remaining <= 0) {
            return NodeStatus.FAILED
        }
        if (condition()) {
            return NodeStatus.SUCCEEDED
        }

        remaining--
        if (remaining == 0) {
            return NodeStatus.FAILED
        }
        return NodeStatus.RUNNING
    }

    override fun parentFinished() {
        remaining = toWait
        super.parentFinished()
    }

    override fun toString(): String = "Awaiting $name ($remaining frames rem)"
}

class Retry(var times: Int, child: Node) : Decorator(child) {
    private var remaining = times
    override fun tick(): NodeStatus {
        if (remaining <= 0) {
            return NodeStatus.FAILED
        }
        val result = super.tick()
        if (result == NodeStatus.FAILED) {
            remaining--
            return NodeStatus.RUNNING
        }
        if (result == NodeStatus.SUCCEEDED) {
            return NodeStatus.SUCCEEDED
        }
        return result
    }

    override fun parentFinished() {
        remaining = times
        super.parentFinished()
    }
}

class Repeat(val times: Int = -1, child: Node) : Decorator(child) {
    var remaining = times
    override fun tick(): NodeStatus {
        if (remaining == 0)
            return NodeStatus.SUCCEEDED
        val result = super.tick()
        if (result == NodeStatus.FAILED)
            return NodeStatus.FAILED
        if (result == NodeStatus.RUNNING)
            return NodeStatus.RUNNING
        super.parentFinished()
        if (remaining > 0) {
            remaining--
        }
        if (remaining == 0) {
            return NodeStatus.SUCCEEDED
        }
        return NodeStatus.RUNNING
    }

    override fun parentFinished() {
        remaining = times
        super.parentFinished()
    }

    override fun toString(): String = "Repeat($remaining) -> $child"
}

class Parallel(val m: Int, vararg val children: Node) : Node {
    override fun tick(): NodeStatus {
        val result = children.map { it.tick() }
        if (result.count { it == NodeStatus.SUCCEEDED } >= m) {
            parentFinished()
            return NodeStatus.SUCCEEDED
        }
        if (result.count { it == NodeStatus.FAILED } > max(0, result.size - m)) {
            parentFinished()
            return NodeStatus.FAILED
        }
        return NodeStatus.RUNNING
    }

    override fun parentFinished() {
        children.forEach { it.parentFinished() }
    }

    override fun toString(): String = "Parallel($m, ${children.joinToString(", ")})"
}

class MParallel(val m: Int, vararg val children: Node) : Node {
    var activeChildren = children.toList()
    var succeeded = 0
    var failed = 0
    override fun tick(): NodeStatus {
        val result = activeChildren.map { it to it.tick() }.unzip()
        succeeded += result.second.count { it == NodeStatus.SUCCEEDED }
        failed += result.second.count { it == NodeStatus.FAILED }
        activeChildren = result.first.filterIndexed { index, node -> result.second[index] == NodeStatus.RUNNING }
        if (succeeded >= m) {
            parentFinished()
            return NodeStatus.SUCCEEDED
        }
        if (failed > max(0, children.size - m)) {
            parentFinished()
            return NodeStatus.FAILED
        }
        return NodeStatus.RUNNING
    }

    override fun parentFinished() {
        succeeded = 0
        failed = 0
        children.forEach { it.parentFinished() }
    }

    override fun toString(): String = "MParallel($m, ${activeChildren.joinToString(", ")})"
}


class DispatchParallel<T : Any>(val childrenResolver: () -> Collection<T>, val name: String? = "", val nodeGen: (T) -> Node) : Node {
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
        return if (result.contains(NodeStatus.FAILED)) {
            parentFinished()
            return NodeStatus.FAILED
        }
        else if (result.contains(NodeStatus.RUNNING))
            NodeStatus.RUNNING
        else {
            parentFinished()
            NodeStatus.SUCCEEDED
        }
    }

    override fun parentFinished() {
        children.clear()
    }

    override fun toString(): String = "$name(${children.values.joinToString(", ")})"
}

//
//class ToNodes<T : Any>(val childrenResolver: () -> Collection<T>, val nodeGen: (T) -> Node) : Node {
//    private val LOG = LogManager.getLogger()
//    private val children = ArrayList<Node>()
//    override fun tick(): NodeStatus {
//        val newItems = childrenResolver()
//        children.addAll(newItems.map(nodeGen))
//
//        val toDelete = children.mapNotNull {
//            val result = it.tick()
//            if (result != NodeStatus.RUNNING)
//                it
//            else
//                null
//        }
//        if (LOG.isInfoEnabled) {
//            toDelete.forEach { LOG.info("Removed $it") }
//        }
//        children.removeAll(toDelete)
//        return if (children.isEmpty()) return NodeStatus.SUCCEEDED else NodeStatus.RUNNING
//    }
//}


//infix fun <T : Node> T.by(utility: () -> Double) = UtilityTask(this) { utility() }
//
//class UtilityFallback(vararg val childArray: UtilityTask, val taskProviders: List<() -> List<UtilityTask>> = emptyList()) : FallbackBase() {
//    override fun children(): List<Node> =
//            (childArray.toList() + taskProviders.flatMap { it() }).sortedByDescending { it.utility() }
//}
//
//class UtilitySequence(vararg val childArray: UtilityTask, val taskProviders: List<() -> List<UtilityTask>> = emptyList()) : SequenceBase() {
//    override fun children(): List<Node> =
//            (childArray.toList() + taskProviders.flatMap { it() }).sortedByDescending { it.utility() }
//}
//
//class UtilityTask(val wrappedTask: Node, val utility: () -> Double) : Node {
//    override fun tick(): NodeStatus = wrappedTask.tick()
//}

object Success : Node {
    override fun tick(): NodeStatus = NodeStatus.SUCCEEDED
    override fun toString(): String = "SUCCESS"
}

object Fail : Node {
    override fun tick(): NodeStatus = NodeStatus.FAILED
    override fun toString(): String = "FAIL"
}

class Sleep(val timeOut: Int = Int.MAX_VALUE) : Node {
    var remaining: Int = timeOut

    override fun tick(): NodeStatus =
            if (remaining == 0)
                NodeStatus.SUCCEEDED
            else {
                if (remaining > 0) {
                    remaining--
                }
                NodeStatus.RUNNING
            }

    override fun parentFinished() {
        remaining = timeOut
    }

    override fun toString(): String = "Sleeping $remaining frames"

    companion object : Node {
        override fun tick(): NodeStatus {
            return NodeStatus.RUNNING
        }

        override fun toString(): String = "Sleeping"
    }
}

abstract class FallbackBase : Node {
    abstract fun children(): List<Node>

    override fun tick(): NodeStatus {
        children().forEach {
            val result = it.tick()
            if (result == NodeStatus.SUCCEEDED) {
                parentFinished()
                return NodeStatus.SUCCEEDED
            }
            if (result == NodeStatus.RUNNING) {
                return NodeStatus.RUNNING
            }
        }
        parentFinished()
        return NodeStatus.FAILED
    }

    override fun parentFinished() {
        children().forEach(Node::parentFinished)
    }

    override fun toString(): String = "fback(${children().joinToString(", ")})"
}

open class Fallback(vararg childArray: Node) : FallbackBase() {
    private val _children: List<Node> = childArray.toList()

    override fun children(): List<Node> = _children

    override fun toString(): String = "Fallback(${_children.size})"
}

abstract class SequenceBase : Node {
    abstract fun children(): List<Node>

    override fun tick(): NodeStatus {
        children().forEach {
            val result = it.tick()
            if (result == NodeStatus.FAILED) {
                parentFinished()
                return NodeStatus.FAILED
            }
            if (result == NodeStatus.RUNNING) {
                return NodeStatus.RUNNING
            }
        }
        parentFinished()
        return NodeStatus.SUCCEEDED
    }

    override fun parentFinished() {
        children().forEach(Node::parentFinished)
    }

    override fun toString(): String = "SEQBASE(${children().joinToString(", ")})"
}

class Sequence(vararg childArray: Node) : SequenceBase() {
    private val _children: List<Node> = childArray.toList()

    override fun children(): List<Node> = _children

    override fun toString(): String = "Sequence(${_children.joinToString(",")})"
}

class MSequence(val name: String, vararg val children: Node) : Node {
    val log = LogManager.getLogger()

    var childIndex = 0

    override fun tick(): NodeStatus {
        while (childIndex < children.size) {
            val result = children[childIndex].tick()
            if (result == NodeStatus.FAILED) {
                parentFinished()
                return NodeStatus.FAILED
            }
            if (result == NodeStatus.RUNNING) {
                return NodeStatus.RUNNING
            }
            childIndex++
        }
        return NodeStatus.SUCCEEDED
    }

    override fun parentFinished() {
        children.forEach { it.parentFinished() }
        childIndex = 0
    }

    override fun toString(): String = "$name@$childIndex(${children.joinToString(",")})"
}

class MFallback(vararg val children: Node) : Node {
    var childIndex = 0

    override fun tick(): NodeStatus {
        do {
            val result = children[childIndex].tick()
            if (result == NodeStatus.SUCCEEDED) {
                parentFinished()
                return NodeStatus.SUCCEEDED
            }
            if (result == NodeStatus.RUNNING) {
                return NodeStatus.RUNNING
            }
        } while (++childIndex < children.size)
        parentFinished()
        return NodeStatus.FAILED
    }

    override fun parentFinished() {
        childIndex = 0
        children.forEach(Node::parentFinished)
    }
}