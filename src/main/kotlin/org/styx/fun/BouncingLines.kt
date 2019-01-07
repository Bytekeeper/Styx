package org.styx.`fun`

import bwapi.Color
import bwapi.Position
import org.styx.Context.frameCount
import org.styx.Context.game
import java.util.*

class BouncingLines {
    private val rnd = SplittableRandom()
    private val vectors = arrayOf(Position(rnd.nextInt(-20, 20), rnd.nextInt(-20, 20)),
            Position(rnd.nextInt(-20, 20), rnd.nextInt(-20, 20)))
    private val lines = mutableListOf(Pair(Position(rnd.nextInt(640), rnd.nextInt(480)),
            Position(rnd.nextInt(640), rnd.nextInt(480))))

    fun reset() {}

    fun update() {
        if (frameCount % 8 == 0) {
            val (a, b) = lines[0]
            var (va, vb) = vectors

            if (a.x + va.x < 0 || a.x + va.x >= 640) {
                va = Position(-va.x, va.y)
            }
            if (a.y + va.y < 0 || a.y + va.y >= 480) {
                va = Position(va.x, -va.y)
            }
            if (b.x + vb.x < 0 || b.x + vb.x > 640) {
                vb = Position(-vb.x, vb.y)
            }
            if (b.y + vb.y < 0 || b.y + vb.y >= 480) {
                vb = Position(vb.x, -vb.y)
            }
            vectors[0] = va
            vectors[1] = vb

            val na = a.add(va)
            val nb = b.add(vb)
            lines.add(0, Pair(na, nb))
            while (lines.size > 10) lines.removeAt(lines.size - 1)
        }
        lines.forEach {
            game.drawLineScreen(it.first, it.second, Color.Blue)
        }
    }
}