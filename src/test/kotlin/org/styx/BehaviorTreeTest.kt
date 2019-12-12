package org.styx

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class BehaviorTreeTest {
    @Test
    fun testSeq() {
        // GIVEN
        val s = Seq("test1",
                {NodeStatus.DONE},
                {NodeStatus.RUNNING})

        // WHEN
        val result = s()

        // THEN
        Assertions.assertThat(result).isEqualTo(NodeStatus.RUNNING)
    }
}
