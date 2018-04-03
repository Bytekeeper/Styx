package org.fttbot

import org.apache.logging.log4j.LogManager
import kotlin.math.max

enum class NodeStatus {
    FAILED,
    SUCCEEDED,
    RUNNING
}

interface Node<in P, out T> {
    val board: T?
    fun setParent(parent: Node<*, P>)
    fun tick(): NodeStatus
    fun parentFinished() {}

    infix fun <X : P> onlyIf(condition: Node<X, *>): Node<X, T> = object : Node<X, T> {
        override val board: T?
            get() = this@Node.board

        override fun setParent(parent: Node<*, X>) {
            this@Node.setParent(parent)
            condition.setParent(parent)
        }

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

abstract class BaseNode<T> : Node<T, T> {
    protected var _parent: Node<*, T>? = null
    override val board get() = _parent?.board

    override fun setParent(parent: Node<*, T>) {
        this._parent = parent
    }
}

abstract class ParentNode<P, T>(protected val convert: (P?) -> T?, protected val children: List<Node<T, *>>) : Node<P, T> {
    override var board: T? = null
    private var parent: Node<*, P>? = null

    init {
        children.forEach { it.setParent(this) }
    }

    override fun setParent(parent: Node<*, P>) {
        this.parent = parent
        resetBoard()
        children.forEach { it.setParent(this) }
    }

    private fun resetBoard() {
        board = convert(parent?.board)
    }

    override fun parentFinished() {
        resetBoard()
        children.forEach { it.parentFinished() }

        super.parentFinished()
    }
}

class Condition<T>(val name: String, val condition: T?.() -> Boolean) : BaseNode<T>() {
    override fun tick(): NodeStatus = if (condition(_parent?.board)) NodeStatus.SUCCEEDED else NodeStatus.FAILED
    override fun toString(): String = "require $name"
}

open class Decorator<T>(val child: Node<T, *>) : BaseNode<T>() {
    override fun tick(): NodeStatus = child.tick()

    override fun parentFinished() {
        child.parentFinished()
    }

    override fun setParent(parent: Node<*, T>) {
        super.setParent(parent)
        child.setParent(parent)
    }
}

class Inline<T>(val name: String, val delegate: T?.() -> NodeStatus) : BaseNode<T>() {
    override fun tick(): NodeStatus = delegate(_parent?.board)

    override fun toString(): String = "Inline $name"
}

class Delegate<T>(val subtreeProducer: T?.() -> Node<T, *>) : BaseNode<T>() {
    private var subtree: Node<T, *>? = null

    override fun tick(): NodeStatus {
        subtree = if (subtree != null) subtree else {
            val result = subtreeProducer(_parent?.board)
            _parent?.let(result::setParent)
            result
        }
        return subtree!!.tick()
    }

    override fun parentFinished() {
        subtree?.parentFinished()
        subtree = null
    }


    override fun setParent(parent: Node<*, T>) {
        this._parent = parent
        super.setParent(parent)
    }
}

class MaxTries<T>(val name: String, var maxTries: Int = Int.MAX_VALUE, child: Node<T, *>) : Decorator<T>(child) {
    private var remaining = maxTries
    override fun tick(): NodeStatus {
        if (remaining <= 0) {
            return NodeStatus.FAILED
        }
        val childResult = super.tick()
        return when (childResult) {
            NodeStatus.SUCCEEDED -> NodeStatus.SUCCEEDED
            NodeStatus.FAILED -> NodeStatus.FAILED
            NodeStatus.RUNNING -> {
                remaining--
                if (remaining == 0) {
                    NodeStatus.FAILED
                } else
                    NodeStatus.RUNNING
            }
        }
    }

    override fun parentFinished() {
        remaining = maxTries
        super.parentFinished()
    }

    override fun toString(): String = "Trying $name ($remaining ticks rem)"
}


class Await<T>(val name: String, var toWait: Int = Int.MAX_VALUE, val condition: () -> Boolean) : BaseNode<T>() {
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

class Retry<T>(var times: Int, child: Node<T, *>) : Decorator<T>(child) {
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

class Repeat<T>(val times: Int = -1, child: Node<T, *>) : Decorator<T>(child) {
    protected val LOG = LogManager.getLogger()
    var remaining = times
    override fun tick(): NodeStatus {
        var maxLoops = 10
        while (remaining != 0 && maxLoops-- > 0) {
            val result = super.tick()
            if (result == NodeStatus.FAILED)
                return NodeStatus.FAILED
            if (result == NodeStatus.RUNNING)
                return NodeStatus.RUNNING
            super.parentFinished()
            if (remaining > 0) {
                remaining--
            }
        }
        if (maxLoops == 0) {
            LOG.error("Possible infinity loop: $this")
        }
        return NodeStatus.SUCCEEDED
    }

    override fun parentFinished() {
        remaining = times
        super.parentFinished()
    }

    override fun toString(): String = "Repeat($remaining) -> $child"
}

class Parallel<P, T>(val m: Int, convert: (P?) -> T?, children: List<Node<T, *>>) : ParentNode<P, T>(convert, children) {
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

    override fun toString(): String = "parallel($m, ${children.joinToString(", ")})"

    companion object {
        fun <T> parallel(m: Int, vararg child: Node<T, *>) = Parallel<T, T>(m, { it }, children = child.toList())
    }
}

class MParallel<P, T>(val m: Int, convert: (P?) -> T?, children: List<Node<T, *>>) : ParentNode<P, T>(convert, children) {
    var activeChildren = super.children
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
        activeChildren = children.toList()
        super.parentFinished()
    }

    override fun toString(): String = "Mparallel($m, ${activeChildren.joinToString(", ")})"

    companion object {
        fun <T> mparallel(m: Int, vararg child: Node<T, T>) = MParallel<T, T>(m, { it }, children = child.toList())
    }
}


class DispatchParallel<T : Any, X>(val childrenResolver: () -> Collection<X>, val name: String? = "", val nodeGen: (X) -> Node<T, *>) : BaseNode<T>() {
    private val LOG = LogManager.getLogger()
    private val children = HashMap<X, Node<T, *>>()
    override fun tick(): NodeStatus {
        val toTransform = childrenResolver()
        val result = toTransform.map {
            val result = children.computeIfAbsent(it) {
                val newNode = nodeGen(it)
                _parent?.let { newNode.setParent(it) }
                newNode
            }.tick()
            if (result == NodeStatus.SUCCEEDED) {
                children.remove(it)
            }
            result
        }
        children.keys.removeIf { !toTransform.contains(it) }
        return if (result.contains(NodeStatus.FAILED)) {
            parentFinished()
            return NodeStatus.FAILED
        } else if (result.contains(NodeStatus.RUNNING))
            NodeStatus.RUNNING
        else {
            parentFinished()
            NodeStatus.SUCCEEDED
        }
    }

    override fun parentFinished() {
        children.clear()
        super.parentFinished()
    }

    override fun toString(): String = "$name(${children.values.joinToString(", ")})"
}


class NoFail<P, T>(child: Node<P, T>) : Decorator<P>(child) {
    override fun tick(): NodeStatus {
        val result = super.tick()
        return when (result) {
            NodeStatus.FAILED -> {
                LogManager.getLogger().error("Unexpected fail: $child")
                NodeStatus.SUCCEEDED
            }
            NodeStatus.RUNNING -> NodeStatus.RUNNING
            NodeStatus.SUCCEEDED -> NodeStatus.SUCCEEDED
        }
    }
}

object Success : BaseNode<Any>() {
    override fun tick(): NodeStatus = NodeStatus.SUCCEEDED
    override fun toString(): String = "SUCCESS"
}

object Fail : BaseNode<Any>() {
    override fun tick(): NodeStatus = NodeStatus.FAILED
    override fun toString(): String = "FAIL"
}

class Sleep<T>(val timeOut: Int = Int.MAX_VALUE) : BaseNode<T>() {
    var startFrame: Int = -1

    override fun tick(): NodeStatus {
        if (startFrame < 0) {
            startFrame = FTTBot.frameCount
        }
        return if (FTTBot.frameCount - startFrame >= timeOut)
            NodeStatus.SUCCEEDED
        else
            NodeStatus.RUNNING
    }

    override fun parentFinished() {
        startFrame = -1
    }

    override fun toString(): String = "Sleeping ${FTTBot.frameCount - startFrame} frames"

    companion object : BaseNode<Any>() {
        override fun tick(): NodeStatus {
            return NodeStatus.RUNNING
        }

        override fun toString(): String = "Sleeping"
    }
}

class Fallback<P, T>(convert: (P?) -> T?, children: List<Node<T, *>>) : ParentNode<P, T>(convert, children) {
    override fun tick(): NodeStatus {
        children.forEach {
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
        children.forEach(Node<T, *>::parentFinished)
    }

    override fun toString(): String = "fallback(${children.joinToString(", ")})"

    companion object {
        fun <T> fallback(vararg child: Node<T, *>) = Fallback<T, T>({ it }, children = child.toList())
        fun <P, T> fallback(convert: (P?) -> T?, vararg child: Node<T, *>) = Fallback<P, T>(convert, children = child.toList())
    }
}

class Sequence<P, T>(convert: (P?) -> T?, children: List<Node<T, *>>) : ParentNode<P, T>(convert, children) {
    override fun tick(): NodeStatus {
        children.forEach {
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

    override fun toString(): String = "Seq(${children.joinToString(", ")})"

    companion object {
        fun <T> sequence(vararg child: Node<T, *>) = Sequence<T, T>({ it }, children = child.toList())
        fun <P, T> sequence(convert: (P?) -> T?, vararg child: Node<T, *>) = Sequence<P, T>(convert, children = child.toList())
    }
}

class MSequence<P, T>(val name: String, convert: (P?) -> T?, children: List<Node<T, *>>) : ParentNode<P, T>(convert, children) {
    private var childIndex = 0

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
        parentFinished()
        return NodeStatus.SUCCEEDED
    }

    override fun parentFinished() {
        childIndex = 0
        super.parentFinished()
    }

    override fun toString(): String = "$name@$childIndex(${children.joinToString(",")})"

    companion object {
        fun <T> msequence(name: String, vararg child: Node<T, *>) = MSequence<T, T>(name, { it }, children = child.toList())
        fun <P, T> msequence(convert: (P?) -> T, name: String, vararg child: Node<T, *>) = MSequence<P, T>(name, convert, children = child.toList())
    }
}
