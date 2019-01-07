package org.styx

import bwapi.Unit

class UnitInfo(unit: Unit) {
    val id = unit.id
    val type = unit.type
    val lastKnownPosition = unit.position

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UnitInfo

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id
    }


}