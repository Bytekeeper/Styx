package org.styx

import bwapi.WalkPosition

infix fun WalkPosition.cross(other: WalkPosition) = x * other.y - other.x * y
