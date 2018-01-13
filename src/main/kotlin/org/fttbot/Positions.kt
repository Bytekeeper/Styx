package org.fttbot

import com.badlogic.gdx.math.Vector2
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.WalkPosition
import kotlin.math.abs
import kotlin.math.sqrt

fun TilePosition.translated(x: Int, y: Int) = TilePosition(this.x + x, this.y + y)
fun Position.translated(x: Int, y: Int) = Position(this.x + x, this.y + y)
fun TilePosition.toVector() = Vector2(this.x.toFloat(), this.y.toFloat())
fun Position.toVector() = Vector2(this.x.toFloat(), this.y.toFloat())
fun Vector2.toPosition() = Position(this.x.toInt(), this.y.toInt())
operator fun Position.plus(other: Position) = Position(other.x + this.x, other.y + this.y)
operator fun Position.minus(other: Position) = Position(this.x - other.x, this.y - other.y)
operator fun Position.div(value: Int): Position = Position(this.x / value, this.y / value)
