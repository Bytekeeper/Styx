package org.styx

import bwapi.UnitType
import org.assertj.core.api.Assertions.assertThat
import org.bk.ass.sim.JBWAPIAgentFactory
import org.junit.jupiter.api.Test

internal class UnitEvaluatorsTest {
    private val factory = JBWAPIAgentFactory()

    @Test
    fun `Cannons are more valuable than goons`() {
        // GIVEN
        val cannonValue = valueOf(UnitType.Protoss_Photon_Cannon)

        // WHEN
        val goonValue = valueOf(UnitType.Protoss_Dragoon)

        // THEN
        assertThat(cannonValue).isGreaterThan(goonValue)
    }

    @Test
    fun `Workers have similar value as Lings`() {
        // GIVEN
        val worker = valueOf(UnitType.Terran_SCV)

        // WHEN
        val ling = valueOf(UnitType.Zerg_Zergling)

        // THEN
        assertThat(worker).isGreaterThanOrEqualTo(ling)
    }

    @Test
    fun `Workers are less valuable than Mutas`() {
        // GIVEN
        val worker = valueOf(UnitType.Terran_SCV)

        // WHEN
        val muta = valueOf(UnitType.Zerg_Mutalisk)

        // THEN
        assertThat(worker).isLessThan(muta)
    }

    @Test
    fun `Hdyras are less valuable than Cannons`() {
        // GIVEN
        val hydra = valueOf(UnitType.Zerg_Hydralisk)

        // WHEN
        val cannon = valueOf(UnitType.Protoss_Photon_Cannon)

        // THEN
        assertThat(hydra).isLessThan(cannon)
    }


    private fun valueOf(unitType: UnitType) = valueOfAgent(factory.of(unitType), unitType)
}