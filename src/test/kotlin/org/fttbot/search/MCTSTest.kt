package org.fttbot.search

import org.fttbot.GameState
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.test.KickStart
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import java.util.*

internal class MCTSTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            KickStart().injectValues()
        }
    }

    @Test
    fun shouldFindBestBO() {
        val state = GameState(0, Race.Terran, 16, 20, 50, 0,
                mutableMapOf(UnitType.Terran_SCV to mutableListOf(
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0)
                ), UnitType.Terran_Command_Center to mutableListOf(GameState.UnitState(0, 0, 0))),
                mutableMapOf(TechType.None to 0), mutableMapOf(UpgradeType.None to GameState.UpgradeState(0, 0, 0)))
        val search = MCTS(mapOf(UnitType.Terran_Vulture to 4, UnitType.Terran_Marine to 2), setOf(), mapOf(UpgradeType.Ion_Thrusters to 1), Race.Terran)
        val start = System.currentTimeMillis()
        repeat(4000) { search.step(state) }
        val prng = Random()
        var n = search.root.children!!.minBy { it.frames }
        println("${n!!.frames} in ${System.currentTimeMillis() - start} ms");
        while (n != null) {
            println(n.move)
            n = n.children?.minBy { it.frames }
        }
    }

}