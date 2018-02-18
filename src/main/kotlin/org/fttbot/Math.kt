package org.fttbot

import kotlin.math.abs

fun fastsig(x: Double) = x / (1.0 + abs(x))