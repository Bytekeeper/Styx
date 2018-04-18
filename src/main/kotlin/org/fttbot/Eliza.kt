package org.fttbot

import org.openbw.bwapi4j.Player
import java.util.*

object Eliza {
    internal val messages = ArrayDeque<Message>()
    private val rnd = Random()
    private val yoMamaReplies = arrayOf(
            "shes on both sides of the family",
            "that when she took a selfie, Instagram crashed",
            "ate a whole Pizza....Hut"
    )


    fun reply(player: Player?, text: String) {
        val parsed = ArrayDeque(text.toLowerCase().trim().split("(\\s|[.;,\\-!])+".toRegex()).filter {
            when (it) {
                "is", "it" -> false
                else -> true
            }
        })
        val baseFrame = FTTBot.frameCount + text.length / 2

        while (!parsed.isEmpty()) {
            when (parsed.poll()) {
                "yo", "your", "you're" -> your(parsed, baseFrame)
                "hey" -> parsed.poll()
                else -> parsed.clear()
            }
        }
    }

    private fun your(parsed: ArrayDeque<String>, baseFrame: Int) {
        when (parsed.poll()) {
            "mama", "momma" ->
                messages.add(Message("And your ${if (rnd.nextBoolean()) "mama" else "momma"} so fat ${yoMamaReplies[rnd.nextInt(yoMamaReplies.size)]}", baseFrame + 30))
        }
        parsed.clear()
    }

    fun step() {
        messages.filter {
            it.atFrame >= FTTBot.frameCount
        }.forEach {
            FTTBot.game.interactionHandler.sendText(it.text)
        }
        messages.removeIf { it.atFrame >= FTTBot.frameCount }
    }


    data class Message(val text: String, val atFrame: Int)
}
