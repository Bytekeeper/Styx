package org.styx.it

import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

class ScbwIt {

    @Test
    fun `Run Styx test game`() {
        Files.walkFileTree(Paths.get(System.getProperty("user.home")).resolve(".scbw/games/GAME_test"), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes?): FileVisitResult {
                Files.delete(file)
                return super.visitFile(file, attrs)
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.delete(dir)
                return super.postVisitDirectory(dir, exc)
            }
        })
        val libname = "FFT_TER-all-1.0-SNAPSHOT.jar"
        Files.copy(Paths.get("build/libs").resolve(libname),
                Paths.get(System.getProperty("user.home")).resolve(".scbw/bots/StyxZ/AI").resolve(libname),
                StandardCopyOption.REPLACE_EXISTING)

        val process = ProcessBuilder().command("scbw.play", "--headless", "--bots", "StyxZ", "McRave", "--game_name", "test")
                .inheritIO()
                .start()
    }
}