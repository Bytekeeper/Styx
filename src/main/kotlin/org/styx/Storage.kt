package org.styx

import bwapi.Race
import com.jsoniter.JsonIterator
import com.jsoniter.output.JsonStream
import org.styx.Styx.readPath
import org.styx.Styx.writePath
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class Storage {
    lateinit var learned: LearningData
        private set

    fun init() {
        val learnFile = readPath.resolve("learnfile.js")
        if (learnFile.toFile().exists()) {
            learned = JsonIterator.deserialize(Files.readAllBytes(learnFile), LearningData::class.java)
        } else {
            learned = LearningData(emptyList())
        }
        save()
    }

    fun appendAndSave(result: GameResult) {
        learned = learned.copy(learned.gameResults.takeLast(5000) + result)
        save()
    }

    private fun save() {
        val learnFileToWrite = writePath.resolve("learnfile.js")
        Files.newOutputStream(learnFileToWrite, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
                .use { out -> JsonStream.serialize(learned, out) }
    }
}

data class LearningData(
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