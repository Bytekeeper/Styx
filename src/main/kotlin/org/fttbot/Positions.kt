package org.fttbot

import bwapi.Position
import bwapi.TilePosition
import bwapi.WalkPosition
import com.badlogic.gdx.math.Vector2
import kotlin.math.abs

fun TilePosition.translated(x: Int, y: Int) = TilePosition(this.x + x, this.y + y)
fun Position.translated(x: Int, y: Int) = Position(this.x + x, this.y + y)
fun Position.toWalkable() = WalkPosition(this.x / 8, this.y / 8)
fun TilePosition.toVector() = Vector2(this.x.toFloat(), this.y.toFloat())
fun Position.toVector() = Vector2(this.x.toFloat(), this.y.toFloat())
fun Vector2.toPosition() = Position(this.x.toInt(), this.y.toInt())
operator fun Position.plus(other: Position) = Position(other.x + this.x, other.y + this.y)
operator fun Position.minus(other: Position) = Position(this.x - other.x, this.y - other.y)
operator fun Position.div(value: Int): Position = Position(this.x / value, this.y / value)

fun approxDistance(dx: Int, dy: Int): Int {
    var min = abs(dx);
    var max = abs(dy);
    if (max < min) {
        val t = max
        max = min
        min = t
    }

    if (min < (max shr 2)) return max

    val minCalc = (3 * min) shr 3;
    return (minCalc shr 5) + minCalc + max - (max shr 4) - (max shr 6);
}
