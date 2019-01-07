package org.styx

import bwapi.Position
import bwapi.TilePosition
import com.badlogic.gdx.math.Vector2


fun TilePosition.translated(x: Int, y: Int) = TilePosition(this.x + x, this.y + y)
fun TilePosition.toVector() = Vector2(x.toFloat(), y.toFloat())


operator fun Position.plus(other: Position) : Position = add(other)
fun Position.translated(x: Int, y: Int) = Position(this.x + x, this.y + y)
