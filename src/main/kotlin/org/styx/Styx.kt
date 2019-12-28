package org.styx

import bwapi.*
import bwem.BWMap
import org.bk.ass.grid.Grid
import org.bk.ass.grid.RayCaster
import org.bk.ass.manage.GMS
import org.bk.ass.query.PositionQueries
import org.bk.ass.sim.Evaluator
import org.bk.ass.sim.RetreatBehavior
import org.bk.ass.sim.Simulator
import org.locationtech.jts.math.Vector2D
import org.styx.Styx.game
import org.styx.Timed.Companion.time
import org.styx.global.*
import org.styx.info.Geography
import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import kotlin.math.*

val positionExtractor: (SUnit) -> org.bk.ass.path.Position = { org.bk.ass.path.Position(it.x, it.y) }

object Styx {
    lateinit var game: Game
    lateinit var map: BWMap
    lateinit var writePath: Path
        private set
    lateinit var readPath: Path
        private set

    lateinit var fingerPrint: FingerPrint
        private set
    lateinit var relevantGameResults: RelevantGames
        private set
    val frameTimes = mutableListOf<Timed>()
    var turnSize = 0
        private set
    var frame = 0
        private set
    private var minRemainingLatencyFrames = 42
        private set
    var units = Units()
        private set
    var resources = Resources()
        private set
    var bases = Bases()
        private set
    var squads = Squads()
        private set
    var buildPlan = BuildPlan()
        private set
    lateinit var self: Player
        private set
    var economy = Economy()
        private set
    val pendingUpgrades = PendingUpgrades()
    var balance = Balance()
        private set
    var tech = Tech()
        private set
    val geography = Geography()

    val sim = Simulator.Builder()
            .build()
    val simFS3 = Simulator.Builder()
            .withFrameSkip(3)
            .build()
    val fleeSim = Simulator.Builder()
            .withPlayerABehavior(RetreatBehavior())
            .withFrameSkip(7)
            .build()
    val evaluator = Evaluator()
    val diag = Diagnose()
    val storage = Storage()
    var strategy = ""

    fun update() {
        frameTimes.clear()
        minRemainingLatencyFrames = min(minRemainingLatencyFrames, game.remainingLatencyFrames)
        turnSize = game.latencyFrames - minRemainingLatencyFrames + 1
        self = game.self()
        if (frame > 0 && game.remainingLatencyFrames > minRemainingLatencyFrames && game.frameCount - frame < minRemainingLatencyFrames || game.isPaused)
            return
        // Opponent fiddled around with speed?
        if (game.frameCount - frame > minRemainingLatencyFrames) {
            // Maybe
            minRemainingLatencyFrames = game.remainingLatencyFrames
        }
        frame = game.frameCount
        ft("units") { units.update() }
        ft("economy") { economy.update() }
        ft("resources") { resources.update() }
        ft("bases") { bases.update() }
        ft("squads") { squads.update() }
        ft("buildPlan") { buildPlan.update() }
        ft("tech") { tech.update() }
        ft("geography") { geography.update() }
    }

    fun onEnd(winner: Boolean) {
        diag.close()
        storage.appendAndSave(
                GameResult(fingerPrint, strategy, winner))
    }

    fun <T> ft(name: String, delegate: () -> T): T {
        var result: T? = null
        frameTimes += time(name) { result = delegate(); Unit }
        return result!!
    }

    fun init() {
        geography.init()
        var path = Paths.get("bwapi-data").resolve("write")
        if (!Files.exists(path)) {
            path = Paths.get("")
            while (Files.list(path).noneMatch { it.fileName.toString() == "write" }) {
                path = path.parent ?: error("Failed to find write folder")
            }
        }
        writePath = path
        println("Writing to folder $path")
        path = Paths.get("bwapi-data").resolve("read")
        if (!Files.exists(path)) {
            path = Paths.get("")
            while (Files.list(path).noneMatch { it.fileName.toString() == "read" }) {
                path = path.parent ?: error("Failed to find write folder")
            }
        }
        readPath = path
        diag.init()
        storage.init()
        fingerPrint = FingerPrint(
                game.enemy().name,
                game.mapFileName(),
                game.startLocations.size,
                game.enemy().race)

        relevantGameResults = RelevantGames(sequenceOf<(GameResult) -> Boolean>(
                { it.fingerPrint == fingerPrint },
                { it.fingerPrint.copy(map = null) == fingerPrint.copy(map = null) },
                { it.fingerPrint.copy(map = null, startLocations = null) == fingerPrint.copy(map = null, startLocations = null) },
                { it.fingerPrint.copy(map = null, startLocations = null, enemy = null) == fingerPrint.copy(map = null, startLocations = null, enemy = null) },
                { it.fingerPrint.copy(map = null, enemy = null, enemyRace = null) == fingerPrint.copy(map = null, enemy = null, enemyRace = null) }
        ).map { storage.learned.gameResults.filter(it) }
                .toList())

        storage.learned.gameResults
                .map { it.strategy to it.won }
                .groupBy({ it.first }) { it.second }
                .forEach { (strat, winLoss) ->
                    val successRate = winLoss.map { if (it) 1.0 else 0.0 }.average()
                    if (winLoss.size >= 10 && successRate < 0.2) {
                        println("Possible shitty strategy: $strat - it was played ${winLoss.size} times but has a winrate of $successRate!")
                    }
                }

        update()
    }
}

data class RelevantGames(private val results: List<List<GameResult>>) {
    val amount = results.sumBy { it.size }
    val score: Double
        get() {
            val aggregated = generateSequence(1.0) { it * 0.8 }.take(results.size)
                    .mapIndexedNotNull { index, factor ->
                        val won = results[index].map { if (it.won) 1.0 else 0.0 }
                        if (won.isEmpty())
                            null
                        else
                            won.average() * factor to factor
                    }.toList()
            return if (aggregated.isEmpty())
                0.5
            else aggregated.reduce { acc, pair ->
                (acc.first + pair.first) to (acc.second + pair.second)
            }.run { first / second }
        }

    fun filteredByStrategy(strategy: String) = RelevantGames(results.map { it.filter { it.strategy == strategy } })
}

val UnitType.dimensions get() = Position(width(), height())

operator fun Position.div(factor: Int) = divide(factor)
operator fun Position.plus(other: Position) = add(other)
operator fun Position.minus(other: Position) = subtract(other)

fun Position.toVector2D() = Vector2D(x.toDouble(), y.toDouble())

operator fun TilePosition.plus(other: TilePosition) = add(other)

operator fun WalkPosition.plus(other: WalkPosition) = add(other)
operator fun WalkPosition.minus(other: WalkPosition) = subtract(other)
infix fun WalkPosition.dot(other: WalkPosition) = x * other.x + y * other.y
fun WalkPosition.middlePosition() = toPosition() + Position(4, 4)
fun WalkPosition.makeValid() = WalkPosition(
        clamp(x, 0, game.mapWidth() * 4 - 1),
        clamp(y, 0, game.mapHeight() * 4 - 1))

fun org.bk.ass.path.Position.toWalkPosition() = WalkPosition(x, y)

fun <T> Optional<T>.orNull(): T? = orElse(null)

fun <T> PositionQueries<T>.inRadius(pos: Position, radius: Int) = inRadius(pos.x, pos.y, radius)
fun <T> PositionQueries<T>.nearest(pos: Position): T? = nearest(pos.x, pos.y)

operator fun GMS.minus(value: GMS) = subtract(value)
operator fun GMS.plus(value: GMS) = add(value)

fun Vector2D.toPosition() = Position(x.roundToInt(), y.roundToInt())
operator fun Vector2D.plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
operator fun Vector2D.minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
operator fun Vector2D.times(scl: Double) = multiply(scl)

fun Double.orZero() = if (isNaN()) 0.0 else this
fun clamp(x: Int, l: Int, h: Int) = min(h, max(l, x))
val PI2 = Math.PI * 2
val Double.normalizedRadians get() = ((this % PI2) + PI2) % PI2 - PI

fun List<SUnit>.nearest(x: Int, y: Int) = minBy { it.distanceTo(Position(x, y)) }
fun List<SUnit>.nearest(x: Int, y: Int, predicate: (SUnit) -> Boolean) = filter(predicate).minBy { it.distanceTo(Position(x, y)) }

fun fastSig(x: Double) = x / (1 + abs(x))

fun RayCaster<Boolean>.tracePath(start: WalkPosition, end: WalkPosition) =
        trace(start.x, start.y, end.x, end.y)?.toWalkPosition()
                ?: start

fun RayCaster<Boolean>.noObstacle(start: WalkPosition, end: WalkPosition) =
        tracePath(start, end) == end

fun RayCaster.Hit<*>.toWalkPosition() = WalkPosition(x, y)

fun Grid<Boolean>.toBufferedImage(): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
    for (y in 0 until height) {
        for (x in 0 until width) {
            image.setRGB(x, y, if (get(x, y)) Color.GREEN.rgb else Color.GRAY.rgb)
        }
    }
    return image
}