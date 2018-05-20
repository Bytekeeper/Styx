package org.fttbot.estimation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.engine.discovery.DiscoverySelectors.selectClass
import org.junit.platform.launcher.LauncherDiscoveryRequest
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.test.BWDataProvider
import org.openbw.bwapi4j.type.UnitType

class CombatEvalTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            BWDataProvider.injectValues();
        }
    }

    @Test
    fun `1 Spore colony should be beaten by 4 Mutas`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Spore_Colony)
        )
        val b = listOf(SimUnit.of(UnitType.Zerg_Mutalisk)
                , SimUnit.Companion.of(UnitType.Zerg_Mutalisk)
                , SimUnit.Companion.of(UnitType.Zerg_Mutalisk)
                , SimUnit.Companion.of(UnitType.Zerg_Mutalisk)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.5)
        assertThat(probabilityToWin).isGreaterThan(0.4)
    }

    @Test
    fun `1 Scourge should not beat 3 Mutas`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Scourge)
        )
        val b = listOf(SimUnit.of(UnitType.Zerg_Mutalisk)
                , SimUnit.Companion.of(UnitType.Zerg_Mutalisk)
                , SimUnit.Companion.of(UnitType.Zerg_Mutalisk)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.4)
    }

    @Test
    fun `2 Marines vs 1 Mutalisk should be evenly matched`() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )
        val b = listOf(SimUnit.of(UnitType.Zerg_Mutalisk))

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isGreaterThan(0.4)
        assertThat(probabilityToWin).isLessThan(0.6)
    }

    @Test
    fun `2 Marines vs 1 Zealot should be evenly matched`() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )
        val b = listOf(SimUnit.of(UnitType.Protoss_Zealot))

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isGreaterThan(0.45)
        assertThat(probabilityToWin).isLessThan(0.7)
    }

    @Test
    fun `2 Marines and 1 Vulture should easily beat 4 Zergling`() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Vulture)
        )
        val b = listOf(SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isGreaterThan(0.6)
    }

    @Test
    fun `2 Marines and 1 Vulture should be no match for 3 Hydras`() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Vulture)
        )
        val b = listOf(SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.4)
    }

    @Test
    fun `2 Marines vs 1 Marine and 1 Medic should be good match`() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Medic)
        )
        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.6)
        assertThat(probabilityToWin).isGreaterThan(0.4)
    }

    @Test
    fun `2 Marines beat 1 Hydralisk`() {
        val a = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )
        val b = listOf(
                SimUnit.of(UnitType.Zerg_Hydralisk)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.7)
        assertThat(probabilityToWin).isGreaterThan(0.5)
    }

    @Test
    fun `2 Zergling vs 1 Zealot should be evenly matched`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Protoss_Zealot)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.55)
        assertThat(probabilityToWin).isGreaterThan(0.45)
    }

    @Test
    fun `2 Mutas vs 4 Zealots should win bigly`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk)
        )
        val b = listOf(
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isGreaterThan(0.7)
    }

    @Test
    fun `2 Muta vs no enemy should win bigly`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk)
        )
        val b = listOf<SimUnit>()

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isGreaterThan(0.5)
    }

    @Test
    fun `Single Muta vs 7 Zealots should win`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Mutalisk)
        )
        val b = listOf(
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isGreaterThan(0.6)
    }

    @Test
    fun `3 Mutas vs 4 Marines and 2 Medics should lose`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Medic),
                SimUnit.of(UnitType.Terran_Medic)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.5)
    }

    @Test
    fun `3 Zergling vs 3 Marines and 1 Medic should lose strongly`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Medic)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.3)
    }

    @Test
    fun `5 Zergling vs 5 Marines should lose`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.5)
    }

    @Test
    fun `5 Zergling vs 8 Marines should lose`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.3)
    }

    @Test
    fun `2 Zerglings are no match for a sieged tank`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Siege_Tank_Siege_Mode)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.35)
    }

    @Test
    fun `2 Zerglings are no match for a Firebat`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Firebat)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.5)
        assertThat(probabilityToWin).isGreaterThan(0.2)
    }

    @Test
    fun `7 Zerglings are no match for 2 sieged tank`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Siege_Tank_Siege_Mode),
                SimUnit.of(UnitType.Terran_Siege_Tank_Siege_Mode)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.4)
    }

    @Test
    fun `8 Zerglings 1 Muta win slightly vs 7 Marines`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.4)
        assertThat(probabilityToWin).isGreaterThan(0.2)
    }

    @Test
    fun `6 Lurkers are losing vs 6 Marines, 2 Medics and 2 Tanks`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Lurker),
                SimUnit.of(UnitType.Zerg_Lurker),
                SimUnit.of(UnitType.Zerg_Lurker),
                SimUnit.of(UnitType.Zerg_Lurker),
                SimUnit.of(UnitType.Zerg_Lurker),
                SimUnit.of(UnitType.Zerg_Lurker)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Medic),
                SimUnit.of(UnitType.Terran_Medic),
                SimUnit.of(UnitType.Terran_Siege_Tank_Siege_Mode),
                SimUnit.of(UnitType.Terran_Siege_Tank_Siege_Mode)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.4)
        assertThat(probabilityToWin).isGreaterThan(0.1)
    }

    @Test
    fun `Attack with Muta only for 2 Mutas, 4 Zerglings vs 8 Zealots`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot)
        )

        val bestProbabilityToWin = CombatEval.bestProbilityToWin(a, b)
        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.5)
        assertThat(bestProbabilityToWin.second).isGreaterThan(0.75)
        assertThat(bestProbabilityToWin.first).allMatch { it.type == UnitType.Zerg_Mutalisk }
    }

    @Test
    fun `3 zerglings vs 3 medics, no challenge`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Medic),
                SimUnit.of(UnitType.Terran_Medic),
                SimUnit.of(UnitType.Terran_Medic)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isGreaterThan(0.6)
    }


    @Test
    fun `5 zerglings vs 1 bunker, no challenge`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling),
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Bunker)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.41)
    }

    @Test
    fun `2 Mutas lose vs 2 Goons`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk)
        )
        val b = listOf(
                SimUnit.of(UnitType.Protoss_Dragoon),
                SimUnit.of(UnitType.Protoss_Dragoon)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b, 192.0)

        assertThat(probabilityToWin).isLessThan(0.41)
    }

    @Test
    fun `8 Mutas win vs 5 Goons`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk)
        )
        val b = listOf(
                SimUnit.of(UnitType.Protoss_Dragoon),
                SimUnit.of(UnitType.Protoss_Dragoon),
                SimUnit.of(UnitType.Protoss_Dragoon),
                SimUnit.of(UnitType.Protoss_Dragoon),
                SimUnit.of(UnitType.Protoss_Dragoon)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b, 192.0)

        assertThat(probabilityToWin).isGreaterThan(0.6)
    }

    @Test
    fun `1 Zergling vs nothing should win`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Zergling)
        )
        val b = listOf<SimUnit>()

        val probabilityToWin = CombatEval.probabilityToWin(a, b, 192.0)

        assertThat(probabilityToWin).isGreaterThan(0.6)
    }

    @Test
    fun `Should need 3 Sunkens vs 7 Zealots`() {
        val b = listOf(
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot)
        )

        val minSunkens = CombatEval.minAmountOfAdditionalsForProbability(emptyList(), SimUnit.of(UnitType.Zerg_Sunken_Colony), b)

        assertThat(minSunkens).isGreaterThan(1)
    }


    @Test
    fun `Should need -1 Spores vs 7 Zealots`() {
        val b = listOf(
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot),
                SimUnit.of(UnitType.Protoss_Zealot)
        )

        val minSunkens = CombatEval.minAmountOfAdditionalsForProbability(emptyList(), SimUnit.of(UnitType.Zerg_Spore_Colony), b)

        assertThat(minSunkens).isEqualTo(-1)
    }

    @Test
    fun `Should need -1 Spores vs nothing`() {
        val b = listOf<SimUnit>()

        val minSunkens = CombatEval.minAmountOfAdditionalsForProbability(emptyList(), SimUnit.of(UnitType.Zerg_Spore_Colony), b)

        assertThat(minSunkens).isEqualTo(-1)
    }

    @Test
    fun `No need for more sunkens with big sunken cluster`() {
        val minSunkens = CombatEval.minAmountOfAdditionalsForProbability(
                listOf(
                        SimUnit.of(UnitType.Zerg_Sunken_Colony),
                        SimUnit.of(UnitType.Zerg_Sunken_Colony),
                        SimUnit.of(UnitType.Zerg_Sunken_Colony),
                        SimUnit.of(UnitType.Zerg_Sunken_Colony),
                        SimUnit.of(UnitType.Zerg_Sunken_Colony),
                        SimUnit.of(UnitType.Zerg_Sunken_Colony),
                        SimUnit.of(UnitType.Zerg_Zergling),
                        SimUnit.of(UnitType.Zerg_Zergling)
                ),
                 SimUnit.of(UnitType.Zerg_Spore_Colony),
                listOf(
                        SimUnit.of(UnitType.Zerg_Zergling),
                        SimUnit.of(UnitType.Zerg_Zergling),
                        SimUnit.of(UnitType.Zerg_Zergling),
                        SimUnit.of(UnitType.Zerg_Zergling),
                        SimUnit.of(UnitType.Zerg_Zergling),
                        SimUnit.of(UnitType.Zerg_Zergling),
                        SimUnit.of(UnitType.Zerg_Zergling),
                        SimUnit.of(UnitType.Zerg_Mutalisk),
                        SimUnit.of(UnitType.Zerg_Scourge)
                ))

        assertThat(minSunkens).isEqualTo(0)
    }

    @Test
    fun `No spores vs zealots`() {
        val minSunkens = CombatEval.minAmountOfAdditionalsForProbability(
                listOf(
                        SimUnit.of(UnitType.Zerg_Sunken_Colony)
                ),
                 SimUnit.of(UnitType.Zerg_Spore_Colony),
                listOf(
                        SimUnit.of(UnitType.Protoss_Zealot),
                        SimUnit.of(UnitType.Protoss_Zealot),
                        SimUnit.of(UnitType.Protoss_Zealot),
                        SimUnit.of(UnitType.Protoss_Zealot),
                        SimUnit.of(UnitType.Protoss_Zealot),
                        SimUnit.of(UnitType.Protoss_Zealot)
                ))

        assertThat(minSunkens).isLessThanOrEqualTo(0)
    }

    @Test
    fun `don't build spores vs ground`() {
        val minSunkens = CombatEval.minAmountOfAdditionalsForProbability(
                listOf(
                        SimUnit.of(UnitType.Zerg_Zergling),
                        SimUnit.of(UnitType.Zerg_Zergling)
                ),
                 SimUnit.of(UnitType.Zerg_Spore_Colony),
                listOf(
                        SimUnit.of(UnitType.Protoss_Zealot)
                ))

        assertThat(minSunkens).isEqualTo(-1)
    }

    @Test
    fun `don't build spores vs ground, no really don't`() {
        val minSunkens = CombatEval.minAmountOfAdditionalsForProbability(
                listOf(
                        SimUnit.of(UnitType.Zerg_Mutalisk)
                ),
                 SimUnit.of(UnitType.Zerg_Spore_Colony),
                listOf(
                        SimUnit.of(UnitType.Protoss_Zealot)
                ))

        assertThat(minSunkens).isEqualTo(0)
    }


    @Test
    fun `Goon can hit muta with range upgrade`() {
        val goon = SimUnit.of(UnitType.Protoss_Dragoon)
        goon.airRange += 2 * 32
        goon.position = Position(0, 0)
        val muta = SimUnit.of(UnitType.Zerg_Mutalisk)
        muta.position = Position(128 + 2 * 32 + UnitType.Zerg_Mutalisk.dimensionLeft() + UnitType.Protoss_Dragoon.dimensionRight(), 0)

        assertThat(goon).matches { it.canAttack(muta) }
    }

    @Test
    fun `Goon can not hit muta without range upgrade`() {
        val goon = SimUnit.of(UnitType.Protoss_Dragoon)
        goon.position = Position(0, 0)
        val muta = SimUnit.of(UnitType.Zerg_Mutalisk)
        muta.position = Position(2 + 128 + UnitType.Zerg_Mutalisk.dimensionLeft() + UnitType.Protoss_Dragoon.dimensionRight(), 0)

        assertThat(goon).matches { !it.canAttack(muta) }
    }

    @Test
    fun `7 Mutas vs 14 Marines should lose`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk)
        )
        val b = listOf(
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine),
                SimUnit.of(UnitType.Terran_Marine)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.5)
    }

    @Test
    fun `7 Mutas vs 10 Hydras should lose`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk),
                SimUnit.of(UnitType.Zerg_Mutalisk)
        )
        val b = listOf(
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk),
                SimUnit.of(UnitType.Zerg_Hydralisk)
        )

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.5)
    }

    @Test
    fun `6 Lurkers lose vs 1 cloaked DT`() {
        val a = listOf(
                SimUnit.of(UnitType.Zerg_Lurker),
                SimUnit.of(UnitType.Zerg_Lurker),
                SimUnit.of(UnitType.Zerg_Lurker),
                SimUnit.of(UnitType.Zerg_Lurker),
                SimUnit.of(UnitType.Zerg_Lurker),
                SimUnit.of(UnitType.Zerg_Lurker)
        )
        val b = listOf(
                SimUnit.of(UnitType.Protoss_Dark_Templar)
        )
        b.forEach { it.detected = false }

        val probabilityToWin = CombatEval.probabilityToWin(a, b)

        assertThat(probabilityToWin).isLessThan(0.3)
    }
}