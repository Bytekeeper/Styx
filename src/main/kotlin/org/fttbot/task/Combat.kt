package org.fttbot.task

import org.fttbot.*
import org.fttbot.estimation.SimUnit
import org.openbw.bwapi4j.unit.Larva
import org.openbw.bwapi4j.unit.PlayerUnit
import kotlin.math.max

object Combat {
    fun attack(units: List<PlayerUnit>, targets: List<PlayerUnit>): Node {
        return Sequence(MMapAll({ units }, "attack") { unit ->
            val simUnit = SimUnit.of(unit)
            val bestTarget = targets.minBy {
                it.hitPoints / max(simUnit.damagePerFrameTo(SimUnit.of(it)), 0.1) +
                        (if (it is Larva) 100 else 0) +
                        it.getDistance(unit) / 2000.0
            }
            if (bestTarget != null && unit.orderTarget != bestTarget)
                Attack(unit, bestTarget)
            else
                Success
        }, Sleep)
    }
}