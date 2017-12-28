package org.fttbot.estimation

import bwapi.Position
import bwapi.UnitType
import org.fttbot.import.FUnitType
import org.junit.jupiter.api.Test


internal class SimulatorTest {

    @Test
    fun simulateA() {
        val a = listOf(SimUnit(type = FUnitType.Terran_Vulture, position = Position(0, 0)))
        val b = listOf(SimUnit(type = FUnitType.Zerg_Zergling, position = Position(30, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                    SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0))
        )
        System.err.println(Simulator.simulate(a, b))
    }

    @Test
    fun simulateB() {
        val a = listOf(SimUnit(type = FUnitType.Terran_Marine, position = Position(0, 0)),
                SimUnit(type = FUnitType.Terran_Vulture, position = Position(0, 0)),
                SimUnit(type = FUnitType.Terran_Wraith, position = Position(0, 0)),
                SimUnit(type = FUnitType.Terran_Marine, position = Position(0, 0)))
        val b = listOf(SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Terran_Marine, position = Position(0, 0)),
                SimUnit(type = FUnitType.Terran_Marine, position = Position(0, 0)),
                SimUnit(type = FUnitType.Terran_Marine, position = Position(0, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0)),
                SimUnit(type = FUnitType.Zerg_Zergling, position = Position(130, 0))
        )
        for (i in 10..50 step 20) {
            for (j in 30..300 step 20) {
                System.err.println("$i, $j: " + Simulator.simulate(a, b, i, j))
            }
        }
    }

    @Test
    fun simulateC() {
        val a = listOf(SimUnit(type = FUnitType.Terran_Marine, position = Position(0, 0)))
        val b = listOf(SimUnit(type = FUnitType.Protoss_Zealot, position = Position(130, 0)))
        for (i in 10..50 step 20) {
            for (j in 30..300 step 20) {
                System.err.println("$i, $j: " + Simulator.simulate(a, b, i, j))
            }
        }
    }

}