package org.styx

import bwapi.*
import bwapi.Unit
import bwem.BWMap
import org.bk.ass.cluster.Cluster
import org.bk.ass.cluster.StableDBScanner
import org.bk.ass.query.PositionAndId
import org.bk.ass.query.UnitFinder
import java.util.*

val positionAndIdExtractor: (SUnit) -> PositionAndId = { PositionAndId(it.id, it.x, it.y) }

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


class Units {
    var ownedUnits = UnitFinder(positionAndIdExtractor)
        private set
    var allunits = UnitFinder(positionAndIdExtractor)
        private set
    var mine = UnitFinder(positionAndIdExtractor)
        private set
    var enemy = UnitFinder(positionAndIdExtractor)
        private set
    var workers = UnitFinder(positionAndIdExtractor)
        private set
    var resourceDepots = UnitFinder(positionAndIdExtractor)
        private set
    var minerals = UnitFinder(positionAndIdExtractor)
        private set
    var geysers = UnitFinder(positionAndIdExtractor)
        private set
    private val myX = mutableMapOf<UnitType, LazyOnFrame<UnitFinder<SUnit>>>()
    private val myCompleted = mutableMapOf<UnitType, LazyOnFrame<UnitFinder<SUnit>>>()

    fun update() {
        allunits = UnitFinder(Styx.game.allUnits.map { SUnit.forUnit(it) }, positionAndIdExtractor)
        allunits.forEach { it.update() }
        ownedUnits = UnitFinder(allunits.filter { it.owned }, positionAndIdExtractor)
        minerals = UnitFinder(Styx.game.minerals.map { SUnit.forUnit(it) }, positionAndIdExtractor)
        geysers = UnitFinder(Styx.game.geysers.map { SUnit.forUnit(it) }, positionAndIdExtractor)

        mine = UnitFinder(Styx.game.self().units.map { SUnit.forUnit(it) }, positionAndIdExtractor)
        resourceDepots = UnitFinder(mine.filter { it.unitType.isResourceDepot }, positionAndIdExtractor)
        workers = UnitFinder(mine.filter { it.unitType.isWorker }, positionAndIdExtractor)

        enemy = UnitFinder(Styx.game.enemy().units.map { SUnit.forUnit(it) }, positionAndIdExtractor)
    }

    fun my(type: UnitType): UnitFinder<SUnit> =
            myX.computeIfAbsent(type) { LazyOnFrame { UnitFinder(mine.filter { it.unitType == type }, positionAndIdExtractor) } }.value

    fun myCompleted(type: UnitType): UnitFinder<SUnit> =
            myCompleted.computeIfAbsent(type) { LazyOnFrame { UnitFinder(mine.filter { it.unitType == type && it.completed }, positionAndIdExtractor) } }.value

    fun onUnitDestroy(unit: Unit) {

    }
}

class Base(val mainResourceDepot: SUnit) {
    val center get() = mainResourceDepot.position
    val centerTile get() = mainResourceDepot.tilePosition
}

class Bases {
    lateinit var myBases: List<Base>
        private set

    fun update() {
        myBases = Styx.map.bases.mapNotNull {
            val base = Styx.units.resourceDepots.closestTo(it.center.x, it.center.y).orNull() ?: return@mapNotNull null
            if (base.distanceTo(it.center) < 80) base else null
        }.map { Base(it) }
    }
}

val UnitType.dimensions get() = Position(width(), height())

operator fun Position.div(factor: Int) = divide(factor)
operator fun Position.plus(other: Position) = add(other)

operator fun TilePosition.plus(other: TilePosition) = add(other)

fun <T> Optional<T>.orNull(): T? = orElse(null)

fun <T> UnitFinder<T>.inRadius(pos: Position, radius: Int) = inRadius(pos.x, pos.y, radius)