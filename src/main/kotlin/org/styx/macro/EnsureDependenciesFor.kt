package org.styx.macro

import bwapi.UnitType
import org.styx.Par

class EnsureDependenciesFor(type: UnitType) : Par("Dependencies for ${type}",
        *type.requiredUnits()
                .filter { (type, _) -> type != UnitType.Zerg_Larva }
                .map { (type, amount) ->
                    Get({amount}, type)
                }.toTypedArray())