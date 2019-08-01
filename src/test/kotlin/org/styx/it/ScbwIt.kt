package org.styx.it

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

class ScbwIt {

    @Test
    fun `Run Styx test game`() {
        val libname = "FFT_TER-all-1.0-SNAPSHOT.jar"
        Files.copy(Paths.get("build/libs").resolve(libname),
                Paths.get(System.getProperty("user.home")).resolve(".scbw/bots/StyxZ/AI").resolve(libname),
                StandardCopyOption.REPLACE_EXISTING)

        val process = ProcessBuilder().command("scbw.play", "--headless", "--bots", "StyxZ", "McRave")
                .inheritIO()
                .start()
    }
}