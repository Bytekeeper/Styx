package org.fttbot.estimation

import org.openbw.bwapi4j.type.UnitType

object UnitVsUnit {
    private val effectivenessDistribution = Array(UnitType.values().size) { i -> Array(UnitType.values().size) { 0.0 } }

    init {
        // Determine initial effectiveness from damage per frame
        // This should actually be "learned"
        UnitType.values().forEach { vs ->
            if (vs.mineralPrice() > 0) {
                val dst = effectivenessDistribution[vs.ordinal]
                val simOfVs = SimUnit.of(vs)
                val dpf = UnitType.values().map { foe -> if (foe.mineralPrice() > 0) SimUnit.of(foe).damagePerFrameTo(simOfVs) else 0.0 }
                val sum = dpf.sum()
                dpf.forEachIndexed { i, damage -> dst[i] = damage / sum }
            }
        }
    }

    fun bestUnitVs(unit: UnitType) = bestUnitVs(UnitType.values().toList(), unit)

    fun bestUnitVs(fromSelection: Collection<UnitType>, unit: UnitType): Pair<UnitType, Double>? {
        val pVs = effectivenessDistribution[unit.ordinal]
        return fromSelection.map { it -> it to pVs[it.ordinal] }.maxBy { (type, p) -> p }
    }
}