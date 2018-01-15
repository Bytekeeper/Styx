package org.fttbot

import java.lang.Integer.max

interface Node<E> {
    fun tick(board: E): NodeStatus
    fun aborted(board: E) {}
    infix fun onlyIf(condition: Node<E>): Node<E> = object : Node<E> {
        override fun tick(board: E): NodeStatus =
                if (condition.tick(board) == NodeStatus.SUCCEEDED)
                    this@Node.tick(board)
                else NodeStatus.FAILED

        override fun toString(): String = "${this@Node} only if ${condition}"
    }
}

class BTree<E>(val root: Node<E>, val board: E) {
    fun tick(): NodeStatus = root.tick(board)
}

enum class NodeStatus {
    FAILED,
    SUCCEEDED,
    RUNNING
}

class Success<E> : Node<E> {
    override fun tick(board: E): NodeStatus {
        return NodeStatus.SUCCEEDED
    }

    override fun toString(): String = "SUCCESS"
}

class Fallback<E>(vararg val children: Node<E>) : Node<E> {
    private var lastRunningChild : Node<E>? = null

    override fun tick(board: E): NodeStatus {
        children.forEach {
            val result = it.tick(board)
            if (result != NodeStatus.FAILED) {
                if (lastRunningChild != it && lastRunningChild != null) {
                    lastRunningChild!!.aborted(board)
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

    override fun aborted(board: E) {
        lastRunningChild?.aborted(board)
        lastRunningChild = null
    }

    override fun toString(): String = "Fallback(${children.size})"
}

class Sequence<E>(vararg val children: Node<E>) : Node<E> {
    private var lastRunningChild : Node<E>? = null

    override fun tick(board: E): NodeStatus {
        children.forEach {
            val result = it.tick(board)
            if (result != NodeStatus.SUCCEEDED) {
                if (lastRunningChild != it && lastRunningChild != null) {
                    lastRunningChild!!.aborted(board)
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

    override fun aborted(board: E) {
        lastRunningChild?.aborted(board)
        lastRunningChild = null
    }

    override fun toString(): String = "Sequence(${children.size})"
}

class MSequence<E>(vararg val children: Node<E>) : Node<E> {
    var childIndex = -1

    override fun tick(board: E): NodeStatus {
        if (childIndex < 0) childIndex = 0
        do {
            val result = children[childIndex].tick(board)
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

    override fun aborted(board: E) {
        if (childIndex >= 0) {
            children[childIndex].aborted(board)
        }
        childIndex = -1
    }
}

class MSelector<E>(vararg val children: Node<E>) : Node<E> {
    var childIndex = -1

    override fun tick(board: E): NodeStatus {
        if (childIndex < 0) childIndex = 0
        do {
            val result = children[childIndex].tick(board)
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

    override fun aborted(board: E) {
        if (childIndex >= 0) {
            children[childIndex].aborted(board)
        }
        childIndex = -1
    }
}