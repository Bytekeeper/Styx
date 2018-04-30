package org.fttbot.task

import org.assertj.core.api.Assertions.assertThat
import org.fttbot.estimation.SimUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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
        val goon = SimUnit.of(UnitType.Protoss_Dragoon)
        val zealot = SimUnit.of(UnitType.Protoss_Zealot)

        val scoreGoon = Combat.attackScore(muta, goon)
        val scoreZealot = Combat.attackScore(muta, zealot)
        assertThat(scoreGoon).isLessThan(scoreZealot)
    }

}