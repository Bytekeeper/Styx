package org.fttbot.task

import javafx.geometry.Pos
import org.assertj.core.api.Assertions.assertThat
import org.fttbot.estimation.SimUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.test.BWDataProvider
import org.openbw.bwapi4j.type.UnitType

internal class CombatTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            BWDataProvider.injectValues();
        }
    }
    @Test
    fun `Muta should attack goon and not Zealot`() {
        val muta = SimUnit.of(UnitType.Zerg_Mutalisk)
        muta.position = Position(0,0)
        val goon = SimUnit.of(UnitType.Protoss_Dragoon)
        goon.position = Position(128, 0)
        val zealot = SimUnit.of(UnitType.Protoss_Zealot)
        zealot.position = Position(0, 0)

        val scoreGoon = Combat.attackScore(muta, goon)
        val scoreZealot = Combat.attackScore(muta, zealot)
        assertThat(scoreGoon).isLessThan(scoreZealot)
    }

    @Test
    fun `Muta should attack corsair and not Zealot`() {
        val muta = SimUnit.of(UnitType.Zerg_Mutalisk)
        muta.position = Position(0,0)
        val corsair = SimUnit.of(UnitType.Protoss_Corsair)
        corsair.position = Position(128, 0)
        val zealot = SimUnit.of(UnitType.Protoss_Zealot)
        zealot.position = Position(0, 0)

        val scoreGoon = Combat.attackScore(muta, corsair)
        val scoreZealot = Combat.attackScore(muta, zealot)
        assertThat(scoreGoon).isLessThan(scoreZealot)
    }
}