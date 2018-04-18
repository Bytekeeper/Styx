package org.fttbot

import org.openbw.bwapi4j.WalkPosition
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

object Map {
    private lateinit var fmap: Array<Array<Int>>
    private lateinit var gmap: Array<Array<Int>>
    private lateinit var closed: Array<Array<Int>>
    private lateinit var open: Array<Array<Int>>
    private lateinit var pred: Array<Array<WalkPosition>>
    private var mClosed = 0
    private var mOpen = 0

    fun init() {
        val height = FTTBot.game.bwMap.mapHeight() * 4
        val width = FTTBot.game.bwMap.mapWidth() * 4
        fmap = Array(height) { Array(width, { 0 }) }
        gmap = Array(height) { Array(width, { 0 }) }
        open = Array(height) { Array(width, { 0 }) }

        closed = Array(height) { y -> Array(width, { x -> if (FTTBot.game.bwMap.isWalkable(x, y)) 0 else Int.MAX_VALUE }) }

        val ZERO = WalkPosition(0, 0)
        pred = Array(height) { Array(width, { ZERO }) }
    }

    fun path(from: WalkPosition, to: WalkPosition): List<WalkPosition> {
        val directions = arrayOf(
                WalkPosition(0, -1),
                WalkPosition(-1, 0), WalkPosition(1, 0),
                WalkPosition(0, 1)
        )
//        val wpComp: (WalkPosition, WalkPosition) -> Int = { w1, w2 -> Integer.compare(w1.x, w2.x) * 2 + Integer.compare(w1.y, w2.y) }
        val openList = PairingHeap<WalkPosition> { p1, p2 -> fmap[p1.y][p1.x].compareTo(fmap[p2.y][p2.x]) }
        gmap[from.y][from.x] = 0
        openList.add(from)
        mClosed++
        mOpen++
        do {
            val node = openList.poll()!!
            if (node == to) {
                val result = ArrayList<WalkPosition>()
                var currentNode = node
                do {
                    result.add(currentNode)
                    currentNode = pred[currentNode.y][currentNode.x]
                } while (currentNode != from)
//                gmap.entries.forEach { (wp, len) ->
//                    val rem = fmap[wp]
//                    if (wp.x % 8 == 0 && wp.y % 8 == 0)
//                        FTTBot.game.mapDrawer.drawTextMap(wp.toPosition(), "$len+$rem")
//                }
//                FTTBot.game.mapDrawer.drawLineMap(from.toPosition(), to.toPosition(), Color.BROWN)
                return result.reversed()
            }
            closed[node.y][node.x] = mClosed
            val cg = gmap[node.y][node.x]
            directions.forEach {
                val wp = node.add(it)
                if (FTTBot.game.bwMap.isValidPosition(wp) && closed[wp.y][wp.x] < mClosed) {
                    val g = cg + 10

                    if (open[wp.y][wp.x] < mOpen || g < gmap[wp.y][wp.x]) {
                        gmap[wp.y][wp.x] = g
                        val d = to.subtract(wp)
                        val h = Math.sqrt((d.x * d.x + d.y * d.y).toDouble()).toInt()
                        val f = g + h * 10
                        if (open[wp.y][wp.x] == mOpen) {
                            openList.remove(wp)
                            fmap[wp.y][wp.x] = min(fmap[wp.y][wp.x], f)
                            openList.offer(wp)
                            pred[wp.y][wp.x] = node
                        } else {
                            fmap[wp.y][wp.x] = f
                            openList.offer(wp)
                            pred[wp.y][wp.x] = node
                        }
                        open[wp.y][wp.x] = mOpen
                    }
                }
            }
        } while (!openList.isEmpty())
        return emptyList()
    }
}