package org.fttbot

import org.apache.logging.log4j.LogManager
import kotlin.math.max

enum class NodeStatus {
    FAILED,
    SUCCEEDED,
    RUNNING
}

data class TreeTraceElement(val node: Node)

interface Node {
    val tree: String get() = toString()

    fun setParent(parent: Node)
    fun tick(): NodeStatus
    fun parentFinished() {}
    fun getTreeTrace(): List<TreeTraceElement> {
        val result = mutableListOf<TreeTraceElement>()
        addTreeTrace(result)
        return result
    }

    fun getFailTrace(): List<TreeTraceElement> {
        val result = mutableListOf<TreeTraceElement>()
        addFailTrace(result)
        return result
    }

    fun addTreeTrace(trace: MutableList<TreeTraceElement>)
    fun addFailTrace(trace: MutableList<TreeTraceElement>)

    infix fun onlyIf(condition: Node): Node = object : Node {
        override fun addTreeTrace(trace: MutableList<TreeTraceElement>) {
            trace.add(TreeTraceElement(this))
            this@Node.addTreeTrace(trace)
        }

        override fun addFailTrace(trace: MutableList<TreeTraceElement>) {
            trace.add(TreeTraceElement(this))
            this@Node.addFailTrace(trace)
        }

        override fun setParent(parent: Node) {
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

abstract class BaseNode : Node {
    protected var _parent: Node? = null

    override fun setParent(parent: Node) {
        this._parent = parent
    }

    override fun addTreeTrace(trace: MutableList<TreeTraceElement>) {
        trace.add(TreeTraceElement(this))
        _parent?.addTreeTrace(trace)
    }

    override fun addFailTrace(trace: MutableList<TreeTraceElement>) {
        trace.add(TreeTraceElement(this))
    }
}

abstract class ParentNode(protected val children: List<Node>) : Node {
    private var _parent: Node? = null
    private var lastFailedChildren = emptyList<Node>()

    init {
        children.forEach { it.setParent(this) }
    }

    override val tree: String
        get() = "$this(${children.map { it.tree }.joinToString(";")})"

    override fun setParent(parent: Node) {
        this._parent = parent
        children.forEach { it.setParent(this) }
    }

    override fun parentFinished() {
        children.forEach { it.parentFinished() }

        super.parentFinished()
    }

    override fun addTreeTrace(trace: MutableList<TreeTraceElement>) {
        trace.add(TreeTraceElement(this))
        _parent?.addTreeTrace(trace)
    }

    override fun addFailTrace(trace: MutableList<TreeTraceElement>) {
        trace.add(TreeTraceElement(this))
        lastFailedChildren.forEach { it.addFailTrace(trace) }
    }

    open protected fun childrenFailed(children: List<Node>) {
        lastFailedChildren = children
    }
}

class Condition(val name: String, val condition: () -> Boolean) : BaseNode() {
    override fun tick(): NodeStatus = if (condition()) NodeStatus.SUCCEEDED else NodeStatus.FAILED
    override fun toString(): String = "Condition $name"
}

open class Decorator(val child: Node) : BaseNode() {
    override val tree: String
        get() = "Decorator(${child.tree})"

    override fun tick(): NodeStatus = child.tick()

    override fun parentFinished() {
        child.parentFinished()
    }

    override fun setParent(parent: Node) {
        super.setParent(parent)
        child.setParent(this)
    }

    override fun addFailTrace(trace: MutableList<TreeTraceElement>) {
        trace.add(TreeTraceElement(this))
        child.addFailTrace(trace)
    }
}

class Inline(val name: String, val delegate: () -> NodeStatus) : BaseNode() {
    override fun tick(): NodeStatus = delegate()

    override fun toString(): String = "Inline $name"
}

class Delegate(val refresh: () -> Boolean = { false }, val subtreeProducer: () -> Node) : BaseNode() {
    private var subtree: Node? = null

    private var lastFailedDelegate: Node? = null

    override fun tick(): NodeStatus {
        subtree = if (subtree != null && !refresh()) subtree else {
            val result = subtreeProducer()
            _parent?.let(result::setParent)
            result
        }
        val result = subtree!!.tick()
        if (result == NodeStatus.FAILED) {
            lastFailedDelegate = subtree
        }
        return result
    }

    override fun parentFinished() {
        subtree?.parentFinished()
        subtree = null
    }

    override fun addFailTrace(trace: MutableList<TreeTraceElement>) {
        trace.add(TreeTraceElement(this))
        lastFailedDelegate?.addFailTrace(trace)
    }

    override fun toString(): String = "Delegate"
}

class MaxTries(val name: String, var maxTries: Int = Int.MAX_VALUE, child: Node) : Decorator(child) {
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


class Await(val name: String, var toWait: Int = Int.MAX_VALUE, val condition: () -> Boolean) : BaseNode() {
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

    override fun toString(): String = "Retrying $remaining / $times times"
}

class Repeat(val times: Int = -1, child: Node) : Decorator(child) {
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

class Parallel(val m: Int, children: List<Node>) : ParentNode(children) {
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
        fun parallel(m: Int, vararg child: Node) = Parallel(m, child.toList())
    }
}

class MParallel(val m: Int, children: List<Node>) : ParentNode(children) {
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
        fun mparallel(m: Int, vararg child: Node) = MParallel(m, children = child.toList())
    }
}


class DispatchParallel<T>(val name: String = "", val boardsToDispatch: () -> Collection<T>, val nodeGen: (T) -> Node) : BaseNode() {
    private val LOG = LogManager.getLogger()
    private val children = HashMap<T, Node>()
    override fun tick(): NodeStatus {
        val toTransform = boardsToDispatch()
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


class NoFail(child: Node) : Decorator(child) {
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

object Success : BaseNode() {
    override fun tick(): NodeStatus = NodeStatus.SUCCEEDED
    override fun toString(): String = "SUCCESS"
}

object Fail : BaseNode() {
    override fun tick(): NodeStatus = NodeStatus.FAILED
    override fun toString(): String = "FAIL"
}

class Sleep(val timeOut: Int = Int.MAX_VALUE) : BaseNode() {
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

    companion object : BaseNode() {
        override fun tick(): NodeStatus {
            return NodeStatus.RUNNING
        }

        override fun toString(): String = "Sleeping"
    }
}

class Fallback(children: List<Node>) : ParentNode(children) {
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
        fun fallback(vararg child: Node) = Fallback(child.toList())
    }
}

open class Sequence(children: List<Node>) : ParentNode(children) {
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
        fun sequence(vararg child: Node) = Sequence(child.toList())
    }
}

class MSequence(val name: String, children: List<Node>) : ParentNode(children) {
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
        fun msequence(name: String, vararg child: Node) = MSequence(name, child.toList())
    }
}
