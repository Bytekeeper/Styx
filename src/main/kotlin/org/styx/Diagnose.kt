package org.styx

import java.io.Closeable
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.util.*

class Diagnose : Closeable {
    private val writePath: Path
    val out: PrintWriter

    init {
        var path = Paths.get("bwapi-data").resolve("write")
        if (!Files.exists(path)) {
            path = Paths.get("")
            while (Files.list(path).noneMatch { it.fileName.toString() == "write" }) {
                path = path.parent ?: error("Failed to find write folder")
            }
        }
        writePath = path
        println("Writing to folder $path")
        val dateTime = LocalDateTime.now().toString().replace(':', '_')
        out = PrintWriter(Files.newBufferedWriter(path.resolve("diagnose-$dateTime.log")), true)
    }

    override fun close() {
        out.close()
    }

    fun log(message: String) {
        val second = Styx.frame / 24
        out.println("%5d (%2d:%2d): %s".format(Styx.frame, second / 60, second % 60, message))
    }

    fun crash(e: Throwable) {
        Files.newBufferedWriter(writePath.resolve("crash-${UUID.randomUUID()}.txt"))
                .use {
                    e.printStackTrace(PrintWriter(it, true))
                }
    }
}