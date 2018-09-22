package org.fttbot.layer

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.fttbot.info.RadiusCache
import org.fttbot.info.allRequiredUnits
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.org.apache.commons.lang3.time.StopWatch
import org.openbw.bwapi4j.test.BWDataProvider
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.UnitImpl
import java.util.*

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

    @Test
    fun testRadiusQuery() {
        val rng = SplittableRandom()
        val units = (1..5000).map { PositionedUnit(Position(rng.nextInt(4000), rng.nextInt(4000))) }
        val radiusCache = RadiusCache(units)

        val a = units.first().getUnitsInRadius(300, units)
        val b = radiusCache.inRadius(units.first(), 300)

        Assertions.assertThat(a).hasSameElementsAs(b)

        val resultA = units.sumBy {
            radiusCache.inRadius(it, 300).size
        }

        val resultB = units.sumBy {
            units.filter { u-> it.getDistance(u) <= 300 }.size
        }

        Assertions.assertThat(resultA).isEqualTo(resultB)


        val stopWatch = StopWatch.createStarted()
        units.forEach {
            radiusCache.inRadius(it, 300)
        }
        stopWatch.stop()
        val time = stopWatch.nanoTime

        stopWatch.reset()
        stopWatch.start()
        units.forEach {
            units.filter { u-> it.getDistance(u) <= 300 }.size
        }
        stopWatch.stop()
        assertThat(time).isLessThan(stopWatch.nanoTime)

    }


    class PositionedUnit(position: Position) : UnitImpl() {
        private val _position = position

        private val myId: Int = PositionedUnit.id++

        override fun getId(): Int  = myId

        override fun getType(): UnitType = UnitType.Zerg_Mutalisk

        override fun getPosition(): Position = _position

        companion object {
            var id : Int = 0
        }
    }
}