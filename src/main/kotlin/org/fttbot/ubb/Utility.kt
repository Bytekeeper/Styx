package org.fttbot.ubb

import org.fttbot.Resources
import org.openbw.bwapi4j.unit.Unit

interface Utility {
    val utility : Double
    fun process(resources: Resources)
}

typealias UtilityProvider = () -> List<Utility>