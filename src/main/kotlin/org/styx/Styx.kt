package org.styx

import bwapi.*
import bwem.BWMap
import org.bk.ass.query.PositionAndId
import org.bk.ass.query.UnitFinder
import java.util.*

object Styx {
    lateinit var game: Game
    lateinit var map: BWMap

    var turnSize = 0
    var frame = 0
    var latencyFrames = 2
    var units = Units()
    var resources = Resources()
    var bases = Bases()
    lateinit var self: Player
        private set

    fun update() {
        latencyFrames = game.latencyFrames
        frame = game.frameCount
        self = game.self()
        units.update()
        resources.update()
        bases.update()
    }
}


class Units {
    private val extractor: (SUnit) -> PositionAndId = { PositionAndId(it.id, it.x, it.y) }
    var mine = UnitFinder(extractor)
        private set
    var workers = UnitFinder(extractor)
        private set
    var resourceDepots = UnitFinder(extractor)
        private set
    var minerals = UnitFinder(extractor)
        private set
    var geysers = UnitFinder(extractor)
        private set

    fun update() {
        mine = UnitFinder(Styx.game.self().units.map { SUnit.forUnit(it) }, extractor)
        resourceDepots = UnitFinder(mine.filter { it.unitType.isResourceDepot }, extractor)
        workers = UnitFinder(mine.filter { it.unitType.isWorker }, extractor)
        minerals = UnitFinder(Styx.game.minerals.map { SUnit.forUnit(it) }, extractor)
        geysers = UnitFinder(Styx.game.geysers.map { SUnit.forUnit(it) }, extractor)
    }
}

class Base(val mainResourceDepot: SUnit?)

class Bases {
    lateinit var myBases: List<Base>
        private set

    fun update() {
        myBases = Styx.map.bases.mapNotNull { Styx.units.resourceDepots.closestTo(it.center.x, it.center.y).orNull() }
                .map { Base(it) }
    }
}

val UnitType.dimensions get() = Position(width(), height())

operator fun Position.div(factor: Int) = divide(factor)
operator fun Position.plus(other: Position) = add(other)

operator fun TilePosition.plus(other: TilePosition) = add(other)

fun <T> Optional<T>.orNull(): T? = orElse(null)