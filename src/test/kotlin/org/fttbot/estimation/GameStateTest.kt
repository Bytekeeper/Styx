package org.fttbot.estimation

import org.fttbot.GameState
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.test.KickStart
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType

class GameStateTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            KickStart().injectValues()
        }
    }

    @Test
    fun shouldNotAllowAddonWithoutParent() {
        val state = GameState(0, Race.Terran, 10, 20, 200, 50,
                mutableMapOf(),
                mutableMapOf(TechType.None to 0), mutableMapOf(UpgradeType.None to GameState.UpgradeState(0, 0, 0)))
        assertThat(state.isValid(UnitType.Terran_Machine_Shop), `is`(false));
    }

    @Test
    fun shouldAllowAddonWithParent() {
        val state = GameState(0, Race.Terran, 10, 20, 200, 50,
                mutableMapOf(UnitType.Terran_Factory to mutableListOf(GameState.UnitState())),
                mutableMapOf(TechType.None to 0), mutableMapOf(UpgradeType.None to GameState.UpgradeState()))
        assertThat(state.isValid(UnitType.Terran_Machine_Shop), `is`(true));
    }

    @Test
    fun shouldNotAllowUnitWhichRequiresMissingAddon() {
        val state = GameState(0, Race.Terran, 10, 20, 200, 50,
                mutableMapOf(UnitType.Terran_Factory to mutableListOf(GameState.UnitState())),
                mutableMapOf(TechType.None to 0), mutableMapOf(UpgradeType.None to GameState.UpgradeState()))
        assertThat(state.isValid(UnitType.Terran_Siege_Tank_Tank_Mode), `is`(false));
    }

    @Test
    fun shouldSetupGameState() {
        val state = GameState(0, Race.Terran, 10, 20, 50, 0,
                mutableMapOf(UnitType.Terran_SCV to mutableListOf(
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0),
                        GameState.UnitState(0, 0, 0)
                ), UnitType.Terran_Command_Center to mutableListOf(GameState.UnitState(0, 0, 0))),
                mutableMapOf(TechType.None to 0), mutableMapOf(UpgradeType.None to GameState.UpgradeState(0, 0, 0)))
        state.train(UnitType.Terran_SCV)
        println(state)
        println()
        state.train(UnitType.Terran_SCV)
        println(state)
        println()
        state.train(UnitType.Terran_SCV)
        println(state)
        println()
        state.train(UnitType.Terran_SCV)
        println(state)
        println()
        state.build(UnitType.Terran_Barracks)
        println(state)
        println()
        state.build(UnitType.Terran_Barracks)
        println(state)
        println()
        state.build(UnitType.Terran_Barracks)
        println(state)
        println()
        state.train(UnitType.Terran_SCV)
        println(state)
        state.finish()
        println(state)
        println(state.isValid(UnitType.Terran_SCV))
    }
}