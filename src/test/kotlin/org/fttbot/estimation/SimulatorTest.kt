package org.fttbot.estimation

import bwapi.Position
import bwapi.UnitType
import org.fttbot.import.FUnitType
import org.junit.jupiter.api.Test


internal class SimulatorTest {

    @Test
    fun blub() {
        System.err.println(org.fttbot.import.FUnitType.of(UnitType.Terran_Marine).groundWeapon)
    }

    @org.junit.jupiter.api.Test
    fun simulate() {
        val a = listOf(SimUnit(type = FUnitType.Terran_Marine, position = Position(0, 0)),
                SimUnit(type = FUnitType.Terran_Marine, position = Position(0, 0)))
        val b = listOf(SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(30, 0)))
        System.err.println(Simulator.simulate(a, b))
    }
}