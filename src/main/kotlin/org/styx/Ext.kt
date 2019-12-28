package org.styx

import bwapi.TilePosition

fun Iterable<Double>.min(default: Double): Double = min() ?: default
fun Iterable<Int>.min(default: Int): Int = min() ?: default


operator fun TilePosition.div(factor: Int) = divide(factor)
