package org.fttbot.estimation

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.test.KickStart
import org.openbw.bwapi4j.type.UnitType

class CombatEvalTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            KickStart().injectValues()
        }
    }

    @Test
    fun evalA() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Spore_Colony)
        )
        val b = listOf(SimUnit.of(UnitType.Zerg_Mutalisk)
                , SimUnit.Companion.of(UnitType.Zerg_Mutalisk)
                , SimUnit.Companion.of(UnitType.Zerg_Mutalisk)
        )
        System.err.println(CombatEval.probabilityToWin(a, b))
    }

    @Test
    fun evalB() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )
        val b = listOf(SimUnit.of(UnitType.Zerg_Mutalisk))
        System.err.println(CombatEval.probabilityToWin(a, b))
    }

    @Test
    fun evalC() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )
        val b = listOf(SimUnit.of(UnitType.Protoss_Zealot))
        System.err.println(CombatEval.probabilityToWin(a, b))
    }
}