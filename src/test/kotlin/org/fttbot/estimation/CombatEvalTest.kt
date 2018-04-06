package org.fttbot.estimation

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.test.BWDataProvider
import org.openbw.bwapi4j.type.UnitType

class CombatEvalTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            BWDataProvider.injectValues();
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
    fun eval0() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Scourge)
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

    @Test
    fun evalD() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Vulture)
        )
        val b = listOf(SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
                )
        System.err.println(CombatEval.probabilityToWin(a, b))
    }

    @Test
    fun evalE() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Vulture)
        )
        val b = listOf(SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk)
        )
        System.err.println(CombatEval.probabilityToWin(a, b))
    }

    @Test
    fun evalF() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Medic)
        )
        System.err.println(CombatEval.probabilityToWin(a, b))
    }

    @Test
    fun evalG() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )
        val b = listOf(
                SimUnit.of(UnitType.Zerg_Hydralisk)
        )

        System.err.println(CombatEval.probabilityToWin(a, b))
    }

    @Test
    fun evalH() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Protoss_Zealot)
        )

        System.err.println(CombatEval.probabilityToWin(a, b))
    }
}