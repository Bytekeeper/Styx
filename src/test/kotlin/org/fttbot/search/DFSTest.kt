package org.fttbot.search

import org.fttbot.sim.GameState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.test.KickStart
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType

internal class DFSTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            KickStart().injectValues()
        }
    }

    @Test
    fun shouldFindBestBO() {
        val state = GameState(0, Race.Terran, 8, 20, 50, 0,
                mutableMapOf(UnitType.Terran_SCV to mutableListOf(
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0)
                ), UnitType.Terran_Command_Center to mutableListOf(GameState.UnitState(0, 0, 0))),
                mutableMapOf(TechType.None to 0), mutableMapOf(UpgradeType.None to GameState.UpgradeState(0, 0, 0)))
        val search = DFS(state, mapOf(UnitType.Terran_Marine to 10))
        val result = search.run(100)
        println(result)
    }

}