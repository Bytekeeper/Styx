package org.fttbot

import bwapi.Position
import bwapi.TilePosition
import com.badlogic.gdx.math.Vector2

fun TilePosition.translated(x : Int, y : Int) = TilePosition(this.x + x, this.y + y)
fun Position.translated(x : Int, y : Int) = Position(this.x + x, this.y + y)
fun TilePosition.toVector() = Vector2(this.x.toFloat(), this.y.toFloat())
fun Position.toVector() = Vector2(this.x.toFloat(), this.y.toFloat())