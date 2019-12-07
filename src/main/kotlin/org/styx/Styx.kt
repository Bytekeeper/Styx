package org.styx

import bwapi.*
import bwapi.Unit
import bwem.BWMap
import org.bk.ass.cluster.Cluster
import org.bk.ass.cluster.StableDBScanner
import org.bk.ass.grid.Grid
import org.bk.ass.grid.RayCaster
import org.bk.ass.manage.GMS
import org.bk.ass.query.PositionQueries
import org.bk.ass.sim.Evaluator
import org.bk.ass.sim.RetreatBehavior
import org.bk.ass.sim.Simulator
import org.locationtech.jts.math.Vector2D
import org.styx.Styx.buildPlan
import org.styx.Styx.diag
import org.styx.Styx.evaluator
import org.styx.Styx.frame
import org.styx.Styx.game
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.Timed.Companion.time
import org.styx.info.Geography
import org.styx.squad.Squad
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
    lateinit var relevantGameResults: List<GameResult>
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

        val bestPrelearnedMatch = sequenceOf<(GameResult) -> Boolean>(
                { it.fingerPrint == fingerPrint },
                { it.fingerPrint.copy(map = null) == fingerPrint.copy(map = null) },
                { it.fingerPrint.copy(map = null, startLocations = null) == fingerPrint.copy(map = null, startLocations = null) },
                { it.fingerPrint.copy(map = null, startLocations = null, enemy = null) == fingerPrint.copy(map = null, startLocations = null, enemy = null) }
        ).map { storage.learned.gameResults.filter(it) }
                .takeWhile { it.size < 10 }
                .toList()

        val matchingPreviousGames =
                if (bestPrelearnedMatch.isNotEmpty() && bestPrelearnedMatch.last().size >= 10)
                    bestPrelearnedMatch.last()
                else
                    bestPrelearnedMatch.firstOrNull { it.isNotEmpty() }
        relevantGameResults = matchingPreviousGames ?: emptyList()
        storage.learned.gameResults
                .map { it.strategy to it.won }
                .groupBy({ it.first }) { it.second }
                .forEach { (strat, winLoss) ->
                    val successRate = winLoss.map { if (it) 1.0 else 0.0 }.average()
                    if (winLoss.size >= 10 && successRate < 0.2) {
                        println("Possible shitty strategy: $strat - it was played ${winLoss.size} times but has a winrate of $successRate!")
                    }
                }
    }
}

class Economy {
    // "Stolen" from PurpleWave
    private val perWorkerPerFrameMinerals = 0.046
    private val perWorkerPerFrameGas = 0.069
    private var workersOnMinerals: Int = 0
    private var workersOnGas: Int = 0
    lateinit var currentResources : GMS
        private set

    private val supplyWithPending: Int by LazyOnFrame {
        Styx.self.supplyTotal() - Styx.self.supplyUsed() +
                units.myPending.sumBy { it.unitType.supplyProvided() - it.unitType.supplyRequired() }
    }

    val supplyWithPlanned: Int
        get() =
            supplyWithPending + buildPlan.plannedUnits.sumBy {
                val u = it.type
                u.supplyProvided() +
                        -u.supplyRequired() +
                        (if (u.whatBuilds().first == UnitType.Zerg_Drone) 1 else 0)
            }

    // TODO: Missing additional supply in the given time
    fun estimatedAdditionalGMSIn(frames: Int): GMS {
        if (frames < 0) {
            println("!!")
        }
        require(frames >= 0)
        val lostWorkers = buildPlan.plannedUnits
                .filter { it.type.isBuilding }
                .mapNotNull { if (it.framesToStart != null && it.framesToStart <= frames) it.framesToStart else null }
                .sortedBy { it }
        var frame = 0
        var gasWorkers = workersOnGas
        var mineralWorkers = workersOnMinerals
        val estimatedAfterWorkerloss = lostWorkers.fold(GMS(0, 0, 0)) { acc, f ->
            val deltaFrames = f - frame
            if (mineralWorkers > 0)
                mineralWorkers--
            else if (gasWorkers > 0)
                gasWorkers--
            frame = f
            acc + GMS((perWorkerPerFrameGas * deltaFrames * gasWorkers).toInt(), (perWorkerPerFrameMinerals * deltaFrames * mineralWorkers).toInt(), 0)
        }
        val deltaFrames = frames - frame
        return estimatedAfterWorkerloss +
                GMS((perWorkerPerFrameGas * deltaFrames * gasWorkers).toInt(), (perWorkerPerFrameMinerals * deltaFrames * mineralWorkers).toInt(), 0)
    }

    fun update() {
        workersOnMinerals = units.myWorkers.count { it.gatheringMinerals }
        workersOnGas = units.myWorkers.count { it.gatheringGas }
        currentResources = GMS(Styx.self.gas(), Styx.self.minerals(), Styx.self.supplyTotal() - Styx.self.supplyUsed())
    }
}

class Squads {
    private val dbScanner = StableDBScanner<SUnit>(2)

    private var clusters: Collection<Cluster<SUnit>> = emptyList()
        private set
    private val _squads = mutableMapOf<Cluster<SUnit>, Squad>()
    val squads = _squads.values

    fun update() {
        clusters = dbScanner.updateDB(units.ownedUnits, 400)
                .scan(-1)
                .clusters
        _squads.keys.retainAll(clusters)

        Styx.squads.clusters.forEach { cluster ->
            val units = cluster.elements.toMutableList()
            _squads.computeIfAbsent(cluster) { Squad() }
                    .update(units)
        }

//        val any = units.mine.filter { !it.unit.isAttacking && !it.gathering && !it.carrying && !it.unit.isMoving && units.mine.inRadius(it, 150).any { it.unit.isAttacking } }
//        if (any.isNotEmpty()) {
//            println("ASS")
//        }
    }
}

class BuildPlan {
    val plannedUnits = mutableListOf<PlannedUnit>()

    fun update() {
        plannedUnits.clear()
    }
}

data class PlannedUnit(val type: UnitType, val framesToStart: Int? = null, val gmsWhilePlanning: GMS = resources.availableGMS)

class Units {
    lateinit var ownedUnits: PositionQueries<SUnit>
        private set
    lateinit var allunits: PositionQueries<SUnit>
        private set
    lateinit var mine: PositionQueries<SUnit>
        private set
    var enemy: PositionQueries<SUnit> = PositionQueries(emptyList(), positionExtractor)
        private set
    lateinit var myWorkers: PositionQueries<SUnit>
        private set
    lateinit var myResourceDepots: PositionQueries<SUnit>
        private set
    lateinit var resourceDepots: PositionQueries<SUnit>
        private set
    lateinit var minerals: PositionQueries<SUnit>
        private set
    lateinit var geysers: PositionQueries<SUnit>
        private set
    lateinit var myPending: List<PendingUnit>
        private set

    fun update() {
        val knownUnits = (Styx.game.allUnits.map { SUnit.forUnit(it) } + enemy).distinct()
        knownUnits.forEach {
            it.update()
            if (it.firstSeenFrame == Styx.frame)
                diag.onFirstSeen(it)
        }
        val relevantUnits = knownUnits
                .filter {
                    it.visible ||
                            !game.isVisible(it.tilePosition) ||
                            (frame - 24 * 60 <= it.lastSeenFrame && !it.detected)
                }
        allunits = PositionQueries(relevantUnits, positionExtractor)
        ownedUnits = PositionQueries(relevantUnits.filter { it.owned }, positionExtractor)
        minerals = PositionQueries(relevantUnits.filter { it.unitType.isMineralField }, positionExtractor)
        geysers = PositionQueries(relevantUnits.filter { it.unitType == UnitType.Resource_Vespene_Geyser }, positionExtractor)

        mine = PositionQueries(relevantUnits.filter { it.myUnit }, positionExtractor)
        resourceDepots = PositionQueries(ownedUnits.filter { it.unitType.isResourceDepot }, positionExtractor)
        myResourceDepots = PositionQueries(resourceDepots.filter { it.unitType.isResourceDepot }, positionExtractor)
        myWorkers = PositionQueries(mine.filter { it.unitType.isWorker }, positionExtractor)
        enemy = PositionQueries((relevantUnits.filter { it.enemyUnit }), positionExtractor)
        myPending = mine
                .filter { !it.isCompleted }
                .map {
                    if (it.remainingBuildTime == 0 && it.unitType != UnitType.Zerg_Larva && it.unitType != UnitType.Zerg_Egg)
                        PendingUnit(it, it.position, it.unitType, 0)
                    else
                        PendingUnit(it, it.position, it.buildType, it.remainingBuildTime)
                }
        if (game.bullets.any { it.source?.type == UnitType.Terran_Bunker }) {
            println("GOT YA")
        }
    }

    fun my(type: UnitType) = mine.filter { it.unitType == type }

    fun myCompleted(type: UnitType) = mine.filter { it.unitType == type && it.isCompleted }

    fun onUnitDestroy(unit: Unit) {
        val u = SUnit.forUnit(unit)
        enemy.remove(u)
        u.dispose()
    }
}

class Tech {
    private lateinit var remainingUpgradeTime: Map<UpgradeType, Int>

    fun update() {
        remainingUpgradeTime = units.mine
                .filter { it.unit.isUpgrading }
                .map { it.unit.upgrade to it.unit.remainingUpgradeTime }
                .toMap()
    }

    fun timeRemainingForUpgrade(upgradeType: UpgradeType): Int? = remainingUpgradeTime[upgradeType]
}

class Base(val centerTile: TilePosition,
           val center: Position,
           val isStartingLocation: Boolean,
           var mainResourceDepot: SUnit? = null,
           var lastSeenFrame: Int? = null,
           var hasGas: Boolean,
           var populated: Boolean = false)

class Bases {
    lateinit var bases: List<Base>
        private set
    lateinit var myBases: List<Base>
        private set
    lateinit var enemyBases: List<Base>
        private set
    lateinit var potentialEnemyBases: List<Base>
        private set

    fun update() {
        if (!this::bases.isInitialized) {
            bases = Styx.map.bases.map {
                Base(it.location, it.center, it.isStartingLocation, hasGas = it.geysers.isNotEmpty())
            }
        }
        bases.forEach {
            val resourceDepot = units.resourceDepots.nearest(it.center.x, it.center.y)
            if (resourceDepot != null && resourceDepot.distanceTo(it.center) < 80)
                it.mainResourceDepot = resourceDepot
            if (game.isVisible(it.centerTile))
                it.lastSeenFrame = frame
            it.populated = units.ownedUnits.nearest(it.center.x, it.center.y) { it.unitType.isBuilding }.distanceTo(it.center) < 600
        }
        myBases = bases.filter { it.mainResourceDepot?.myUnit == true }
        enemyBases = (bases - myBases).filter { it.populated }
        potentialEnemyBases = bases.filter {
            val closestEnemy = units.enemy.nearest(it.center)?.distanceTo(it.center) ?: 0
            it.isStartingLocation &&
                    !it.populated &&
                    !game.isExplored(it.centerTile) &&
                    (closestEnemy < 300 || closestEnemy > 600)
        }
    }
}

class Balance {
    val globalFastEval by LazyOnFrame {
        val myAgents = units.mine.map { it.agent() }
        val enemyAgents = units.enemy.map { it.agent() }
        evaluator.evaluate(myAgents, enemyAgents)
    }
    val direSituation get() = globalFastEval < 0.2
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

fun Grid<Boolean>.toBufferedImage() : BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
    for (y in 0 until height) {
        for (x in 0 until width) {
            image.setRGB(x, y, if (get(x, y)) Color.GREEN.rgb else Color.GRAY.rgb)
        }
    }
    return image
}