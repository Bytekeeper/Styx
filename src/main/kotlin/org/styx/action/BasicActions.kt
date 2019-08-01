package org.styx.action

import bwapi.Position
import bwapi.TilePosition
import bwapi.UnitType
import org.styx.SUnit
import org.styx.dimensions
import org.styx.div
import org.styx.plus


object Construct {
    fun construct(worker: SUnit, type: UnitType, at: TilePosition) {
        if (worker.distanceTo(at) < 5) {
            worker.build(type, at)
            return
        }
        BasicActions.move(worker, at.toPosition() + type.dimensions / 2)
    }
}

object BasicActions {
    fun move(unit: SUnit, to: Position) {
        unit.moveTo(to)
    }

    fun gather(unit: SUnit, resource: SUnit) {
        if (unit.target == resource || unit.isReturningMinerals) return
        unit.gather(resource)
    }

    fun train(unit: SUnit, type: UnitType) {
        unit.train(type)
    }
}