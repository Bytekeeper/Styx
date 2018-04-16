package org.fttbot

import org.openbw.bwapi4j.WalkPosition
import java.util.*
import kotlin.collections.ArrayList

object Map {
    class Node(val position: WalkPosition, val distance: Int, val estimatedDistance: Int, val predecessor: Node? = null)

    fun path(from: WalkPosition, to: WalkPosition): List<WalkPosition> {
        val directions = arrayOf(
                WalkPosition(-1, -1), WalkPosition(0, -1), WalkPosition(1, -1),
                WalkPosition(-1, 0), WalkPosition(1, 0),
                WalkPosition(-1, 1), WalkPosition(0, 1), WalkPosition(1, 1)
        )
        val closedSet = TreeSet<WalkPosition> { w1, w2 -> Integer.compare(w1.x, w2.y) * 2 + Integer.compare(w1.y, w2.y) }
        val openList = PriorityQueue<Node> { p1, p2 -> Integer.compare(p1.distance, p2.distance) }
//        openList.add(Node(from, 0))
        do {
            val node = openList.poll()
            if (node.position == to) {
                val result = ArrayList<WalkPosition>()
                var currentNode = node
                do {
                    result.add(currentNode.position)
                    currentNode = currentNode.predecessor
                } while (currentNode != null)
                return result.reversed()
            }
            closedSet.add(node.position)
            directions.forEach {
                val wp = node.position.add(it)
                if (FTTBot.bwem.data.mapData.isValid(wp) && FTTBot.bwem.data.getMiniTile(wp).isWalkable) {
//                    openList.add(Node(wp, node.distance + 1, node))
                }
            }
        } while (!openList.isEmpty())
        return emptyList()
    }
}