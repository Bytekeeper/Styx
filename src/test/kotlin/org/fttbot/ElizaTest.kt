package org.fttbot

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.openbw.bwapi4j.Player

internal class ElizaTest {
    @Test
    fun yoMamaTest() {
        Eliza.reply(null, "Hey xyz Yo mama   so fat she left the house in high heels and when she came back she had on flip flops.")

        assertThat(Eliza.messages).anyMatch { it.text.contains("And yo") }
    }
}
