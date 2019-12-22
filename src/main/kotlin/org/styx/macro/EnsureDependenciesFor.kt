package org.styx.macro

import bwapi.UnitType
import org.styx.BehaviorTree
import org.styx.Par
import org.styx.SimpleNode
import org.styx.Styx

class EnsureDependenciesFor(private val type: UnitType) : BehaviorTree("Dependencies for $type") {
    override fun buildRoot() : SimpleNode = Par("Dependencies for $type", false,
            *type.requiredUnits()
                    .filter { (type, _) -> type != UnitType.Zerg_Larva }
                    .map { (type, amount) ->
                        Get({ amount }, type)
                    }.toTypedArray(),
            Get({ if (type.gasPrice() > 0) 1 else 0 }, Styx.self.race.refinery))
}
