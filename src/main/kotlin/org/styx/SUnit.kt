package org.styx

import bwapi.Position
import bwapi.TilePosition
import bwapi.Unit
import bwapi.UnitType
import org.bk.ass.info.BWMirrorUnitInfo
import kotlin.math.max

class SUnit private constructor(val unit: Unit) {
    private var readyOnFrame = 0
    var x = unit.x
    var y = unit.y
    val initialTilePosition = unit.initialTilePosition
    val tilePosition: TilePosition get() = unit.tilePosition
    val id: Int = unit.id
    var unitType = unit.type
        private set

    val ready get() = readyOnFrame >= Styx.frame
    val sleeping get() = readyOnFrame < Styx.frame
    val target: SUnit? get() = unit.target?.let { forUnit(it) }
    val isReturningMinerals get() = unit.isCarrying && unit.target?.type?.isResourceDepot == true
    val controller = Controller()

    fun distanceTo(other: SUnit) = unit.getDistance(other.unit)
    fun distanceTo(pos: TilePosition) = unit.tilePosition.getDistance(pos)

    fun moveTo(target: Position) {
        if (sleeping) return
        unit.move(target)
        sleep()
    }

    fun build(type: UnitType, at: TilePosition) {
        if (sleeping) return
        unit.build(type, at)
        sleep()
    }

    fun attack(other: SUnit) {
        if (sleeping) return
        unit.attack(other.unit)
        sleep(BWMirrorUnitInfo.stopFrames(unitType))
    }

    fun gather(resource: SUnit) {
        if (sleeping) return
        unit.gather(resource.unit)
        sleep()
    }

    private fun sleep(minFrames: Int = 2) {
        readyOnFrame = Styx.frame + Styx.latencyFrames + max(minFrames, Styx.turnSize)
    }

    fun train(type: UnitType) {
        unit.train(type)
    }

    companion object {
        private val units = mutableMapOf<Unit, SUnit>()
        fun forUnit(unit: Unit) = units.computeIfAbsent(unit) { SUnit(it) }
    }
}

class Controller {
    fun reset() {

    }
}