package org.styx

import java.util.*

fun <T> Optional<T>.orNull() = orElse(null)