package org.fttbot


import org.openbw.bwapi4j.org.apache.commons.lang3.Validate
import java.util.*
import javax.naming.OperationNotSupportedException

/**
 * https://en.wikipedia.org/wiki/Pairing_heap
 */
class PairingHeap<E>(val comparator: (E, E) -> Int) : AbstractQueue<E>() {
    private var root: PairingTree<E>? = null
    override val size: Int get() = throw UnsupportedOperationException()

    override fun iterator(): MutableIterator<E> {
        return object : MutableIterator<E> {
            override fun remove() = throw OperationNotSupportedException()

            private val stack = ArrayDeque<PairingTree<E>>()

            init {
                if (root != null) {
                    stack.addFirst(root)
                }
            }

            override fun hasNext(): Boolean {
                return !stack.isEmpty()
            }

            override fun next(): E {
                val next = stack.peekFirst()
                if (next.child != null) {
                    stack.addFirst(next.child)
                } else {
                    var current: PairingTree<E>? = stack.removeFirst()
                    while (current != null && current.next == null) {
                        current = if (stack.isEmpty()) null else stack.removeFirst()
                    }
                    if (current != null) {
                        stack.addFirst(current.next)
                    }
                }
                return next.element
            }
        }
    }

    override fun offer(e: E): Boolean {
        Validate.notNull(e)
        root = merge(root, PairingTree(e))
        return true
    }

    override fun poll(): E? {
        if (root == null) {
            return null
        }
        val result = root!!.element
        root = mergePairs(root!!.child)
        return result
    }

    private fun mergePairs(first: PairingTree<E>?): PairingTree<E>? {
        if (first == null) {
            return null
        }
        val second = first.next ?: return first
        return merge(merge(first, second), mergePairs(second.next))
    }

    override fun peek(): E? {
        return if (root == null) {
            null
        } else root!!.element
    }

    private fun merge(a: PairingTree<E>?, b: PairingTree<E>?): PairingTree<E>? {
        if (a == null) {
            return b
        }
        if (b == null) {
            return a
        }
        if (comparator(a.element, b.element) < 0) {
            b.next = a.child
            a.child = b
            return a
        }
        a.next = b.child
        b.child = a
        return b
    }

    private class PairingTree<E>(internal var element: E) {
        internal var next: PairingTree<E>? = null
        internal var child: PairingTree<E>? = null
    }
}
