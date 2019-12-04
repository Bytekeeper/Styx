package org.styx

import bwapi.Color
import bwapi.Position
import bwapi.UnitType
import bwapi.WalkPosition
import com.github.luben.zstd.ZstdOutputStream
import com.jsoniter.any.Any
import com.jsoniter.output.JsonStream
import org.styx.Styx.writePath
import java.awt.image.RenderedImage
import java.io.Closeable
import java.io.PrintWriter
import java.nio.file.Files
import java.util.*
import java.util.logging.Level
import javax.imageio.ImageIO

fun WalkPosition.diag() = "($x,$y)"
fun SUnit.diag() = "i$id ${unitType.shortName()} ${position.toWalkPosition().diag()}"

class Diagnose : Closeable {
    private lateinit var jsonOut: JsonStream
    private lateinit var  logOut: PrintWriter
    private var firstLog = true
    private val drawCommands = mutableMapOf<String, MutableList<DrawCommand>>()
    private var firstSeen = mutableMapOf<String, MutableList<FirstSeen>>()

    fun init() {
        val trace = ZstdOutputStream(Files.newOutputStream(Styx.writePath.resolve("trace.json")))
        jsonOut = JsonStream(trace, 4096)
        logOut = PrintWriter(Files.newBufferedWriter(Styx.writePath.resolve("log.log")), true)
        if (Config.logEnabled) {
            jsonOut.writeObjectStart()
            jsonOut.writeObjectField("_version")
            jsonOut.writeVal(0)
            jsonOut.writeMore()
            jsonOut.writeObjectField("types_names")
            Any.wrap(UnitType.values()
                    .map { it.ordinal.toString() to it.name.substringAfter("_") }
                    .toMap()).writeTo(jsonOut)
            jsonOut.writeMore()
            arrayOf("board_updates", "units_logs", "units_updates",
                    "tensors_summaries", "game_values").forEach {
                jsonOut.writeObjectField(it)
                jsonOut.writeEmptyObject()
                jsonOut.writeMore()
            }
            arrayOf("tasks", "trees", "heatmaps").forEach {
                jsonOut.writeObjectField(it)
                jsonOut.writeEmptyArray()
                jsonOut.writeMore()
            }
            jsonOut.writeObjectField("logs")
            jsonOut.writeArrayStart()
        } else {
            jsonOut.close()
        }
    }

    override fun close() {
        if (!Config.logEnabled) return

        jsonOut.writeArrayEnd()
        jsonOut.writeMore()
        jsonOut.writeObjectField("draw_commands")
        Any.wrap(drawCommands).writeTo(jsonOut)
        jsonOut.writeMore()
        jsonOut.writeObjectField("units_first_seen")
        Any.wrap(firstSeen).writeTo(jsonOut)
        jsonOut.writeObjectEnd()
        jsonOut.close()
        logOut.close()
    }

    fun log(message: String, logLevel: Level = Level.INFO) {
        logOut.println("${logLevel.name} - ${Styx.frame}: $message")
    }

    fun dump(name: String, image: RenderedImage) {
        Files.newOutputStream(writePath.resolve(name + ".jpg"))
                .use { out ->
                    ImageIO.write(image, "jpg", out)
                }
    }

    fun traceLog(message: String, vararg attach: DAttachment = arrayOf()) {
        if (!Config.logEnabled) return
        if (firstLog)
            firstLog = false
        else
            jsonOut.writeMore()
        val trace = Thread.currentThread().stackTrace[2]
        Any.wrap(LogEntry(Styx.frame, message, trace.className, trace.lineNumber, 0, attach.toList())).writeTo(jsonOut)
    }

    fun onFirstSeen(unit: SUnit) {
        if (!Config.logEnabled) return
        firstSeen.computeIfAbsent(Styx.frame.toString()) { mutableListOf() }
                .add(FirstSeen(unit.id, unit.unitType.ordinal, unit.x, unit.y))
    }

    // Seems unsupported:
    fun drawLine(a: Position, b: Position, color: Color) {
        drawCommands.computeIfAbsent(Styx.frame.toString()) { mutableListOf() }
                .add(DrawCommand(DrawType.DrawLine.code, listOf(a.x, a.y, b.x, b.y, color.id), null, emptyList()))
    }

    fun crash(e: Throwable) {
        Files.newBufferedWriter(writePath.resolve("crash-${UUID.randomUUID()}.txt"))
                .use {
                    e.printStackTrace(PrintWriter(it, true))
                }
    }

    data class LogEntry(val frame: Int,
                        val message: String,
                        val file: String = "null",
                        val line: Int = 1,
                        val sev: Int = 0,
                        val attachments: List<kotlin.Any> = emptyList())

    data class DrawCommand(val code: Int,
                           val args: List<Int>,
                           val str: String?,
                           val cherrypi_ids_args_indices: List<Int>
    )
}

enum class DrawType(val code: Int) {
    DrawLine(20), //  x1, y1, x2, y2, color index
    DrawUnitLine(21), // uid1, uid2, color index
    DrawUnitPosLine(22), // uid, x2, y2, color index
    DrawCircle(23), //  x, y, radius, color index
    DrawUnitCircle(24), // uid, radius, color index
    DrawText(25), // x, y plus text arg
    DrawTextScreen(26), // x, y plus text arg
}

interface DAttachment {
    val key_type: String
    val value_type: String
}

class StringKeyMapAttachment(override val value_type: String,
                             val map: Map<String, kotlin.Any>) : DAttachment {
    override val key_type: String = "std::string"
}

class ObjectKeyMapAttachment(override val key_type: String,
                             override val value_type: String,
                             val map: List<List<kotlin.Any>>) : DAttachment

data class DUnit(val id: Int) {
    val type = "unit"
}

data class FirstSeen(val id: Int,
                     val type: Int,
                     val x: Int,
                     val y: Int)