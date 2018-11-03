package org.fttbot

import bwem.typedef.CPPath
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.org.apache.commons.lang3.mutable.MutableInt

class Path(val path: CPPath, val length: Int)

fun path(start: Position, target: Position): Path {
    val length = MutableInt()
    val cpPath = FTTBot.bwem.getPath(start, target, length)
    return Path(cpPath, length.value)
}