package org.fttbot.layer

import org.fttbot.info.allRequiredUnits
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.test.BWDataProvider
import org.openbw.bwapi4j.type.UnitType

internal class UnitQueryKtTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            BWDataProvider.injectValues();
        }
    }

    @Test
    fun shouldReturnAllRequiredUnits() {
        println(UnitType.Terran_Marine.allRequiredUnits())
    }
}