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

    @Test
    fun `Attack Carrier and not Interceptor`() {
        val muta = SimUnit.of(UnitType.Zerg_Mutalisk)
        val enemies = arrayOf(SimUnit.of(UnitType.Protoss_Carrier), SimUnit.of(UnitType.Protoss_Interceptor))

        val target = enemies.map { it to Combat.attackScore(muta, it) }.minBy { it.second }
        assertThat(target).hasFieldOrPropertyWithValue("first.type", UnitType.Protoss_Carrier)
    }

    @Test
    fun `Attack Hydra and not Den`() {
        val muta = SimUnit.of(UnitType.Zerg_Mutalisk)
        val enemies = arrayOf(SimUnit.of(UnitType.Zerg_Hydralisk_Den), SimUnit.of(UnitType.Zerg_Hydralisk))

        val target = enemies.map { it to Combat.attackScore(muta, it) }.minBy { it.second }
        assertThat(target).hasFieldOrPropertyWithValue("first.type", UnitType.Zerg_Hydralisk)
    }

    @Test
    fun `Attack Firebat and not Depot`() {
        val lurker = SimUnit.of(UnitType.Zerg_Lurker)
        val enemies = arrayOf(SimUnit.of(UnitType.Terran_Firebat), SimUnit.of(UnitType.Terran_Supply_Depot))

        val target = enemies.map { it to Combat.attackScore(lurker, it) }.minBy { it.second }
        assertThat(target).hasFieldOrPropertyWithValue("first.type", UnitType.Terran_Firebat)
    }
}