package org.fttbot.estimation

import bwapi.Position
import org.fttbot.import.FUnitType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CombatEvalTest {
    @Test
    fun evalA() {
        val a = listOf(
                SimUnit(type = FUnitType.Terran_Marine, position = Position(0, 0)),
                SimUnit(type = FUnitType.Terran_Medic, position = Position(0, 0))
        )
        val b = listOf(SimUnit(type = FUnitType.Protoss_Zealot, position = Position(130, 0)))
        System.err.println(CombatEval.probabilityToWin(a, b))
    }
}