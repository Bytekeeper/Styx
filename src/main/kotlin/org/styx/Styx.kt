package org.styx

import bwapi.*
import bwapi.Unit
import bwem.BWMap
import org.bk.ass.cluster.Cluster
import org.bk.ass.cluster.StableDBScanner
import org.bk.ass.manage.GMS
import org.bk.ass.query.PositionQueries
import org.styx.Styx.units
import java.util.*

val positionExtractor: (SUnit) -> org.bk.ass.path.Position = { org.bk.ass.path.Position(it.x, it.y) }

object Styx {
    lateinit var game: Game
    lateinit var map: BWMap

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
    var clusters = Clusters()
        private set
    var buildPlan = BuildPlan()
        private set
    lateinit var self: Player
        private set

    fun update() {
        latencyFrames = game.latencyFrames
        frame = game.frameCount
        self = game.self()
        units.update()
        resources.update()
        bases.update()
        clusters.update()
        buildPlan.update()
    }
}

class Clusters {
    private val dbScanner = StableDBScanner<SUnit>(3)

    var clusters: Collection<Cluster<SUnit>> = emptyList()
        private set

    fun update() {
        dbScanner.updateDB(Styx.units.ownedUnits, 400)
                .scan(-1)
        clusters = dbScanner.clusters
    }
}

class BuildPlan {
    val pendingUnits = mutableListOf<UnitType>()

    fun update() {
        pendingUnits.clear()
    }
}

class Units {
    lateinit var ownedUnits: PositionQueries<SUnit>
        private set
    lateinit var allunits: PositionQueries<SUnit>
        private set
    lateinit var mine: PositionQueries<SUnit>
        private set
    lateinit var enemy: PositionQueries<SUnit>
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

    fun update() {
        val allSUnits = Styx.game.allUnits.map { SUnit.forUnit(it) }
        allSUnits.forEach { it.update() }
        allunits = PositionQueries(allSUnits, positionExtractor)
        ownedUnits = PositionQueries(allSUnits.filter { it.owned }, positionExtractor)
        minerals = PositionQueries(allSUnits.filter { it.unitType.isMineralField }, positionExtractor)
        geysers = PositionQueries(allSUnits.filter { it.unitType == UnitType.Resource_Vespene_Geyser }, positionExtractor)

        mine = PositionQueries(allSUnits.filter { it.myUnit }, positionExtractor)
        resourceDepots = PositionQueries(mine.filter { it.unitType.isResourceDepot }, positionExtractor)
        workers = PositionQueries(mine.filter { it.unitType.isWorker }, positionExtractor)

        enemy = PositionQueries(allSUnits.filter { it.enemyUnit }, positionExtractor)
    }

    fun my(type: UnitType): PositionQueries<SUnit> =
            myX.computeIfAbsent(type) { LazyOnFrame { PositionQueries(mine.filter { it.unitType == type }, positionExtractor) } }.value

    fun myCompleted(type: UnitType): PositionQueries<SUnit> =
            myCompleted.computeIfAbsent(type) { LazyOnFrame { PositionQueries(mine.filter { it.unitType == type && it.completed }, positionExtractor) } }.value

    fun onUnitDestroy(unit: Unit) {

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
            it.mainResourceDepot = if (resourceDepot.distanceTo(it.center) < 80) resourceDepot else null
        }
        myBases = bases.filter { it.mainResourceDepot?.myUnit == true }
    }
}

val UnitType.dimensions get() = Position(width(), height())

operator fun Position.div(factor: Int) = divide(factor)
operator fun Position.plus(other: Position) = add(other)

operator fun TilePosition.plus(other: TilePosition) = add(other)

fun <T> Optional<T>.orNull(): T? = orElse(null)

fun <T> PositionQueries<T>.inRadius(pos: Position, radius: Int) = inRadius(pos.x, pos.y, radius)

operator fun GMS.minus(value: GMS) = subtract(value)