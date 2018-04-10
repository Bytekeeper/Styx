package org.fttbot

import org.apache.logging.log4j.LogManager
import kotlin.math.max

enum class NodeStatus {
    FAILED,
    SUCCEEDED,
    RUNNING
}

data class TreeTraceElement(val node: Node<*, *>)

interface Node<in P, out T> {
    val board: T?
    val tree: String get() = toString()

    fun setParent(parent: Node<*, P>)
    fun tick(): NodeStatus
    fun parentFinished() {}
    fun getTreeTrace(): List<TreeTraceElement> {
        val result = mutableListOf<TreeTraceElement>()
        addTreeTrace(result)
        return result
    }

    fun addTreeTrace(trace: MutableList<TreeTraceElement>)

    infix fun <X : P> onlyIf(condition: Node<X, *>): Node<X, T> = object : Node<X, T> {
        override val board: T?
            get() = this@Node.board

        override fun addTreeTrace(trace: MutableList<TreeTraceElement>) {
            trace.add(TreeTraceElement(this))
            this@Node.addTreeTrace(trace)
        }

        override fun setParent(parent: Node<*, X>) {
            this@Node.setParent(parent)
            condition.setParent(parent)
        }

        override fun tick(): NodeStatus =
                if (condition.tick() == NodeStatus.SUCCEEDED)
                    this@Node.tick()
                else NodeStatus.FAILED

        override fun toString(): String = "only if"

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

    override fun addTreeTrace(trace: MutableList<TreeTraceElement>) {
        trace.add(TreeTraceElement(this))
        _parent?.addTreeTrace(trace)
    }
}

abstract class ParentNode<P, T>(protected val convert: (P?) -> T?, protected val children: List<Node<T, *>>) : Node<P, T> {
    override var board: T? = null
    private var parent: Node<*, P>? = null
    private var lastFailedChildren = emptyList<Node<T, *>>()

    init {
        children.forEach { it.setParent(this) }
    }

    override val tree: String
        get() = "$this(${children.map { it.tree }.joinToString(";")})"

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

    override fun addTreeTrace(trace: MutableList<TreeTraceElement>) {
        trace.add(TreeTraceElement(this))
        parent?.addTreeTrace(trace)
    }

    open protected fun childrenFailed(children: List<Node<T, *>>) {
        lastFailedChildren = children
    }
}

class Condition<T>(val name: String, val condition: T?.() -> Boolean) : BaseNode<T>() {
    override fun tick(): NodeStatus = if (condition(_parent?.board)) NodeStatus.SUCCEEDED else NodeStatus.FAILED
    override fun toString(): String = "Condition $name"
}

open class Decorator<T>(val child: Node<T, *>) : BaseNode<T>() {
    override val tree: String
        get() = "Decorator(${child.tree})"

    override fun tick(): NodeStatus = child.tick()

    override fun parentFinished() {
        child.parentFinished()
    }

    override fun setParent(parent: Node<*, T>) {
        super.setParent(parent)
        child.setParent(this)
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

    override fun toString(): String = "Delegate"
}

class MaxTries<T>(val name: String, var maxTries: Int = Int.MAX_VALUE, child: Node<T, *>) : Decorator<T>(child) {
    private var remaining = maxTries
    override fun tick(): NodeStatus {
        if (remaining <= 0) {
            return NodeStatus.FAILED
        }
        remaining--
        return super.tick()
    }

    override fun parentFinished() {
        remaining = maxTries
        super.parentFinished()
    }

    override fun toString(): String = "Trying $name ($remaining / $maxTries ticks rem)"
}


class Await<T>(val name: String, var toWait: Int = Int.MAX_VALUE, val condition: () -> Boolean) : BaseNode<T>() {
    private var startFrame = -1
    override fun tick(): NodeStatus {
        if (startFrame < 0) {
            startFrame = FTTBot.frameCount
        }
        if (FTTBot.frameCount - startFrame >= toWait)
            return NodeStatus.FAILED

        if (condition()) {
            return NodeStatus.SUCCEEDED
        }

        return NodeStatus.RUNNING
    }

    override fun parentFinished() {
        startFrame = -1
        super.parentFinished()
    }

    override fun toString(): String = "Awaiting $name for max ${startFrame - FTTBot.frameCount + toWait} frames)"
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

    override fun toString(): String = "Retrying $remaining / $times times"
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

    override fun toString(): String = "Repeat $remaining / $times"
}

class Parallel<P, T>(val m: Int, convert: (P?) -> T?, children: List<Node<T, *>>) : ParentNode<P, T>(convert, children) {
    override fun tick(): NodeStatus {
        val result = children.map { it.tick() }
        if (result.count { it == NodeStatus.SUCCEEDED } >= m) {
            parentFinished()
            return NodeStatus.SUCCEEDED
        }

        val failedChildren = result.count { it == NodeStatus.FAILED }
        if (failedChildren > 0) {
            childrenFailed(result.mapIndexedNotNull { index, nodeStatus -> if (nodeStatus != NodeStatus.FAILED) null else children[index] })
        }
        if (failedChildren > max(0, result.size - m)) {
            parentFinished()
            return NodeStatus.FAILED
        }
        return NodeStatus.RUNNING
    }

    override fun toString(): String = "Parallel SUCCESS > $m => SUCCESS"

    companion object {
        fun <T> parallel(m: Int, vararg child: Node<T, *>) = Parallel<T, T>(m, { it }, children = child.toList())
    }
}

class MParallel<P, T>(val m: Int, convert: (P?) -> T?, children: List<Node<T, *>>) : ParentNode<P, T>(convert, children) {
    var activeChildren = super.children
    var succeeded = 0
    var failed = 0
    override fun tick(): NodeStatus {
        val childResult = activeChildren.map { it to it.tick() }
        val result = childResult.unzip()
        succeeded += result.second.count { it == NodeStatus.SUCCEEDED }
        failed += result.second.count { it == NodeStatus.FAILED }
        activeChildren = result.first.filterIndexed { index, _ -> result.second[index] == NodeStatus.RUNNING }
        if (succeeded >= m) {
            parentFinished()
            return NodeStatus.SUCCEEDED
        }
            val failedChildren = childResult.filter { it.second == NodeStatus.FAILED }.map { it.first }
        if (failed > 0) {
            childrenFailed(failedChildren)
        }
        if (failed > max(0, children.size - m)) {
            LogManager.getLogger().error("Failed: $this; failed children: ${failedChildren.joinToString(", ")}: ${getTreeTrace().joinToString("\n")}")
            parentFinished()
            failedChildren[0].tick()
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

    override fun toString(): String = "Parallel SUCCESS > $m => SUCCESS, $succeeded successful children, $failed failed children and ${activeChildren.size} / ${children.size} remaining children"

    companion object {
        fun <T> mparallel(m: Int, vararg child: Node<T, T>) = MParallel<T, T>(m, { it }, children = child.toList())
    }
}


class DispatchParallel<T : Any, X>(val name: String = "", val childrenResolver: T?.() -> Collection<X>, val nodeGen: (X) -> Node<T, *>) : BaseNode<T>() {
    private val LOG = LogManager.getLogger()
    private val children = HashMap<X, Node<T, *>>()
    override fun tick(): NodeStatus {
        val toTransform = childrenResolver(board)
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

    override fun toString(): String = "DispatchParallel $name with ${children.size} active child nodes"
}


class NoFail<P, T>(child: Node<P, T>) : Decorator<P>(child) {
    override fun tick(): NodeStatus {
        val result = super.tick()
        return when (result) {
            NodeStatus.FAILED -> {
                LogManager.getLogger().error("Unexpected fail: $child: ${getTreeTrace().joinToString("\n")}")
                NodeStatus.SUCCEEDED
            }
            NodeStatus.RUNNING -> NodeStatus.RUNNING
            NodeStatus.SUCCEEDED -> NodeStatus.SUCCEEDED
        }
    }

    override fun toString(): String = "NoFail"
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

    override fun toString(): String = "Sleep ${FTTBot.frameCount - startFrame + timeOut} frames"

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
            childrenFailed(listOf(it))
        }
        parentFinished()
        return NodeStatus.FAILED
    }

    override fun toString(): String = "Fallback"

    companion object {
        fun <T> fallback(vararg child: Node<T, *>) = Fallback<T, T>({ it }, children = child.toList())
        fun <P, T> fallback(convert: (P?) -> T?, vararg child: Node<T, *>) = Fallback<P, T>(convert, children = child.toList())
    }
}

open class Sequence<P, T>(convert: (P?) -> T?, children: List<Node<T, *>>) : ParentNode<P, T>(convert, children) {
    override fun tick(): NodeStatus {
        children.forEach {
            val result = it.tick()
            if (result == NodeStatus.FAILED) {
                childrenFailed(listOf(it))
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

    override fun toString(): String = "Sequence"

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
                childrenFailed(listOf(children[childIndex]))
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

    override fun toString(): String = "MSequence $name, active child $childIndex"

    companion object {
        fun <T> msequence(name: String, vararg child: Node<T, *>) = MSequence<T, T>(name, { it }, children = child.toList())
        fun <P, T> msequence(convert: (P?) -> T, name: String, vararg child: Node<T, *>) = MSequence<P, T>(name, convert, children = child.toList())
    }
}
