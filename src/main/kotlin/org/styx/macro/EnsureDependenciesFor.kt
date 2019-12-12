package org.styx.macro

import bwapi.UnitType
import org.styx.BehaviorTree
import org.styx.Par
import org.styx.SimpleNode

class EnsureDependenciesFor(type: UnitType) : BehaviorTree("Dependencies for $type") {
    override val root: SimpleNode = Par("Dependencies for $type", false,
            *type.requiredUnits()
                    .filter { (type, _) -> type != UnitType.Zerg_Larva }
                    .map { (type, amount) ->
                        Get({ amount }, type)
                    }.toTypedArray())
}
