package org.styx

import bwapi.*
import bwapi.Unit
import bwem.BWMap
import org.bk.ass.cluster.Cluster
import org.bk.ass.cluster.StableDBScanner
import org.bk.ass.manage.GMS
import org.bk.ass.query.PositionQueries
import org.bk.ass.sim.*
import org.locationtech.jts.math.Vector2D
import org.styx.Styx.frame
import org.styx.Styx.game
import org.styx.Styx.units
import org.styx.Timed.Companion.time
import org.styx.squad.NoAttackIfGatheringBehavior
import org.styx.squad.SquadBoard
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val positionExtractor: (SUnit) -> org.bk.ass.path.Position = { org.bk.ass.path.Position(it.x, it.y) }

object Styx {
    lateinit var game: Game
    lateinit var map: BWMap

    val frameTimes = mutableListOf<Timed>()
    var turnSize = 0
        private set
    var frame = 0
        private set
    var latencyFrames = 2
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
    val sim = Simulator.Builder()
            .withPlayerBBehavior(Simulator.RoleBasedBehavior(NoAttackIfGatheringBehavior(), HealerBehavior(), RepairerBehavior(), SuiciderBehavior()))
            .build()
    val fleeSim = Simulator.Builder()
            .withPlayerABehavior(RetreatBehavior())
            .withFrameSkip(7)
            .build()

    fun update() {
        frameTimes.clear()
        latencyFrames = game.latencyFrames
        frame = game.frameCount
        self = game.self()
        ft("units") { units.update() }
        ft("resources") { resources.update() }
        ft("bases") { bases.update() }
        ft("squads") { squads.update() }
        ft("buildPlan") { buildPlan.update() }
        ft("economy") { economy.update() }
    }

    fun ft(name: String, delegate: () -> kotlin.Unit) {
        frameTimes += time(name, delegate)
    }
}

class Economy {
    // "Stolen" from PurpleWave
    private val perWorkerPerFrameMinerals = 0.046
    private val perWorkerPerFrameGas = 0.069

    private val supplyWithPending: Int by LazyOnFrame {
        Styx.self.supplyTotal() - Styx.self.supplyUsed() +
                units.myPending.sumBy { it.unitType.supplyProvided() - it.unitType.supplyRequired() }
    }

    val supplyWithPlanned: Int
        get() =
        supplyWithPending + Styx.buildPlan.plannedUnits.sumBy { it.supplyProvided() - it.supplyRequired() }

    fun estimatedAdditionalGMIn(frames: Int): GMS =
            GMS((perWorkerPerFrameGas * frames * units.workers.count { it.gatheringGas }).toInt(), (perWorkerPerFrameMinerals * frames * units.workers.count { it.gatheringMinerals }).toInt(), 0)

    fun update() {
    }
}

class Squads {
    private val dbScanner = StableDBScanner<SUnit>(3)
    private val evaluator = Evaluator()

    private var clusters: Collection<Cluster<SUnit>> = emptyList()
        private set
    private val _squads = mutableMapOf<Cluster<SUnit>, SquadBoard>()
    val squads = _squads.values

    fun update() {
        clusters = dbScanner.updateDB(units.ownedUnits, 400)
                .scan(-1)
                .clusters
        _squads.keys.retainAll(clusters)

        Styx.squads.clusters.forEach { cluster ->
            val units = cluster.elements.toMutableList()
            with(_squads.computeIfAbsent(cluster) { SquadBoard() }) {
                mine = units.filter { it.myUnit }
                enemies = units.filter { it.enemyUnit }
                all = units
                fastEval = when {
                    mine.isNotEmpty() && enemies.isNotEmpty() ->
                        evaluator.evaluate(
                                mine.filter { it.unitType.canAttack() }.map { it.agent() },
                                enemies.filter { it.unitType.canAttack() }.map { it.agent() })
                    mine.isEmpty() -> 0.0
                    else -> 1.0
                };
            }
        }

    }
}

class BuildPlan {
    val plannedUnits = mutableListOf<UnitType>()

    fun update() {
        plannedUnits.clear()
    }

    fun showPlanned(base: Position) {
        var pos = base

        plannedUnits.forEach {
            game.drawTextScreen(base, "$it")
            pos = Position(pos.x, pos.y + 10);
        }
    }
}

class Units {
    lateinit var ownedUnits: PositionQueries<SUnit>
        private set
    lateinit var allunits: PositionQueries<SUnit>
        private set
    lateinit var mine: PositionQueries<SUnit>
        private set
    var enemy: PositionQueries<SUnit> = PositionQueries(emptyList(), positionExtractor)
        private set
    lateinit var workers: PositionQueries<SUnit>
        private set
    lateinit var resourceDepots: PositionQueries<SUnit>
        private set
    lateinit var minerals: PositionQueries<SUnit>
        private set
    lateinit var geysers: PositionQueries<SUnit>
        private set
    private val myX = mutableMapOf<UnitType, LazyOnFrame<PositionQueries<SUnit>>>()
    private val myCompleted = mutableMapOf<UnitType, LazyOnFrame<PositionQueries<SUnit>>>()
    lateinit var myPending: List<PendingUnit>
        private set

    fun update() {
        val knownUnits = (Styx.game.allUnits.map { SUnit.forUnit(it) } + enemy).distinct()
        knownUnits.forEach { it.update() }
        val relevantUnits = knownUnits
                .filter {
                    it.visible ||
                            !game.isVisible(it.tilePosition) ||
                            (frame - 240 <= it.lastSeenFrame && !it.detected)
                }
        allunits = PositionQueries(relevantUnits, positionExtractor)
        ownedUnits = PositionQueries(relevantUnits.filter { it.owned }, positionExtractor)
        minerals = PositionQueries(relevantUnits.filter { it.unitType.isMineralField }, positionExtractor)
        geysers = PositionQueries(relevantUnits.filter { it.unitType == UnitType.Resource_Vespene_Geyser }, positionExtractor)

        mine = PositionQueries(relevantUnits.filter { it.myUnit }, positionExtractor)
        resourceDepots = PositionQueries(mine.filter { it.unitType.isResourceDepot }, positionExtractor)
        workers = PositionQueries(mine.filter { it.unitType.isWorker }, positionExtractor)
        enemy = PositionQueries((relevantUnits.filter { it.enemyUnit }), positionExtractor)
        myPending = mine
                .filter { !it.completed }
                .map {
                    if (it.remainingBuildTime == 0 && it.unitType != UnitType.Zerg_Larva && it.unitType != UnitType.Zerg_Egg)
                        PendingUnit(it, it.position, it.unitType, 0)
                    else
                        PendingUnit(it, it.position, it.buildType, it.remainingBuildTime)
                }
    }

    fun my(type: UnitType): PositionQueries<SUnit> =
            myX.computeIfAbsent(type) { LazyOnFrame { PositionQueries(mine.filter { it.unitType == type }, positionExtractor) } }.value

    fun myCompleted(type: UnitType): PositionQueries<SUnit> =
            myCompleted.computeIfAbsent(type) { LazyOnFrame { PositionQueries(mine.filter { it.unitType == type && it.completed }, positionExtractor) } }.value

    fun onUnitDestroy(unit: Unit) {
        val u = SUnit.forUnit(unit)
        enemy.remove(u)
        u.dispose()
    }
}

class Base(val centerTile: TilePosition,
           val center: Position,
           var mainResourceDepot: SUnit? = null)

class Bases {
    lateinit var bases: List<Base>
        private set
    lateinit var myBases: List<Base>
        private set

    fun update() {
        if (!this::bases.isInitialized) {
            bases = Styx.map.bases.map {
                Base(it.location, it.center)
            }
        }
        bases.forEach {
            val resourceDepot = units.resourceDepots.nearest(it.center.x, it.center.y)
            it.mainResourceDepot = if (resourceDepot != null && resourceDepot.distanceTo(it.center) < 80) resourceDepot else null
        }
        myBases = bases.filter { it.mainResourceDepot?.myUnit == true }
    }
}

val UnitType.dimensions get() = Position(width(), height())

operator fun Position.div(factor: Int) = divide(factor)
operator fun Position.plus(other: Position) = add(other)
operator fun Position.minus(other: Position) = subtract(other)

fun Position.toVector2D() = Vector2D(x.toDouble(), y.toDouble())

operator fun TilePosition.plus(other: TilePosition) = add(other)

fun <T> Optional<T>.orNull(): T? = orElse(null)

fun <T> PositionQueries<T>.inRadius(pos: Position, radius: Int) = inRadius(pos.x, pos.y, radius)

operator fun GMS.minus(value: GMS) = subtract(value)
operator fun GMS.plus(value: GMS) = add(value)

fun Vector2D.toPosition() = Position(x.roundToInt(), y.roundToInt())
operator fun Vector2D.plus(other: Vector2D) = Vector2D(x + other.x, y + other.y)
operator fun Vector2D.minus(other: Vector2D) = Vector2D(x - other.x, y - other.y)
operator fun Vector2D.times(scl: Double) = multiply(scl)

fun Double.orZero() = if (isNaN()) 0.0 else this
fun clamp(x: Int, l: Int, h: Int) = min(h, max(l, x))