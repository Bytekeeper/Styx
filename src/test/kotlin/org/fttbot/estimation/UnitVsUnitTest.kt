package org.fttbot.estimation

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.test.BWDataProvider
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.UnitType

internal class UnitVsUnitTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            BWDataProvider.injectValues();
        }
    }

    @Test
    fun test() {
        System.err.println(UnitVsUnit.bestUnitVs(UnitType.values().filter { it.whatBuilds().first.race == Race.Terran }, UnitType.Terran_Marine))
    }
}