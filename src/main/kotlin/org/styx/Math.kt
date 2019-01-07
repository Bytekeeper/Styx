package org.styx

fun Double.or(other: Double) = if (isNaN()) other else this