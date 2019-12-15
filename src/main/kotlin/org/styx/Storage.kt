package org.styx

import bwapi.Race
import com.jsoniter.JsonIterator
import com.jsoniter.output.JsonStream
import org.styx.Styx.readPath
import org.styx.Styx.writePath
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.streams.asSequence

class Storage {
    lateinit var learned: LearningData
        private set
    private lateinit var learnedPerBot: Map<String, LearningData>

    fun init() {
        learnedPerBot = Files.list(readPath).asSequence()
                .filter { Files.isReadable(it) && it.toString().endsWith("-learned.js") }
                .map {
                    val l = JsonIterator.deserialize(Files.readAllBytes(it), LearningData::class.java)
                    l.name!! to l
                }.toMap()

        learned = learnedPerBot.values
                .fold(LearningData(null, emptyList())) { acc, next ->
                    LearningData(null, acc.gameResults + next.gameResults)
                }
    }

    fun appendAndSave(result: GameResult) {
        val botName = result.fingerPrint.enemy!!
        val learnedBefore = learnedPerBot[botName] ?: LearningData(botName, emptyList())
        learnedPerBot = learnedPerBot + (botName to learnedBefore.copy(gameResults = (learnedBefore.gameResults + result).takeLast(300)))
        save(botName)
    }

    private fun save(botName: String) {
        val sanitizedName = botName.replace("[^a-zA-Z0-9]".toRegex(), "_")
        val learnFileToWrite = writePath.resolve("$sanitizedName-learned.js")
        Files.newOutputStream(learnFileToWrite, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
                .use { out -> JsonStream.serialize(learnedPerBot[botName], out) }
    }
}

data class LearningData(
        val name: String? = null,
        val gameResults: List<GameResult> = mutableListOf()
)

data class GameResult(
        var fingerPrint: FingerPrint = FingerPrint(),
        var strategy: String = "",
        var won: Boolean = false
)

data class FingerPrint(
        var enemy: String? = null,
        var map: String? = null,
        var startLocations: Int? = null,
        var enemyRace: Race? = null
)