package org.fttbot.layer

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.fttbot.info.MyUnitFinder
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
        val unitFinder = MyUnitFinder(units)

        val a = units.first().getUnitsInRadius(300, units)
        val b = unitFinder.inRadius(units.first(), 300)

        Assertions.assertThat(a).hasSameElementsAs(b)

        val resultA = units.sumBy {
            unitFinder.inRadius(it, 300).size
        }

        val resultB = units.sumBy {
            units.filter { u -> it.getDistance(u) <= 300 }.size
        }

        Assertions.assertThat(resultA).isEqualTo(resultB)


        val stopWatch = StopWatch.createStarted()
        units.forEach {
            unitFinder.inRadius(it, 300)
        }
        stopWatch.stop()
        val time = stopWatch.nanoTime

        stopWatch.reset()
        stopWatch.start()
        units.forEach {
            units.filter { u -> it.getDistance(u) <= 300 }.size
        }
        stopWatch.stop()
        assertThat(time).isLessThan(stopWatch.nanoTime)

    }


    class PositionedUnit(position: Position) : UnitImpl() {
        private val _position = position

        private val myId: Int = PositionedUnit.id++

        override fun getId(): Int = myId

        override fun getType(): UnitType = UnitType.Zerg_Mutalisk

        override fun getPosition(): Position = _position
        override fun getX(): Int = _position.x
        override fun getY(): Int = _position.y
        override fun getLeft(): Int = _position.x
        override fun getRight(): Int = _position.x - 1
        override fun getTop(): Int = _position.y
        override fun getBottom(): Int = _position.y - 1

        companion object {
            var id: Int = 0
        }
    }
}