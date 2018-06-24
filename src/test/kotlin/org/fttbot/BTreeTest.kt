package org.fttbot

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

internal class BTreeTest {

    @Test
    fun `Fallback should start with highest utility`() {
        val seq = UFallback(Utility({1.0}, Success), Utility({0.0}, Sleep))
        Assertions.assertThat(seq.tick()).isEqualTo(NodeStatus.SUCCEEDED)
    }

    @Test
    fun `Fallback should end with lowest utility`() {
        val seq = UFallback(Utility({1.0}, Sleep), Utility({0.0}, Success))
        Assertions.assertThat(seq.tick()).isEqualTo(NodeStatus.RUNNING)
    }
}