package org.styx

import bwapi.*
import bwapi.Unit
import org.bk.ass.info.BWMirrorUnitInfo
import org.styx.Styx.self
import kotlin.math.max

class SUnit private constructor(val unit: Unit) {
    private var readyOnFrame = 0
    var x = unit.x
        private set
    var y = unit.y
        private set
    var visible = true
        private set
    var detected = unit.isDetected
        private set
    val initialTilePosition = unit.initialTilePosition
    var tilePosition = unit.tilePosition
        private set
    var position = unit.position
        private set
    val id: Int = unit.id
    var myUnit = unit.player == Styx.self
        private set
    var unitType = unit.type
        private set
    var flying = unit.isFlying
        private set
    var carryingGas = unit.isCarryingGas
        private set
    var carryingMinerals = unit.isCarryingMinerals
        private set
    var gatheringGas = unit.isGatheringGas
        private set
    var owned = !unit.player.isNeutral
        private set
    val ready get() = readyOnFrame < Styx.frame
    val sleeping get() = readyOnFrame >= Styx.frame
    var target: SUnit? = unit.target?.let { forUnit(it) }
    val returningMinerals get() = unit.isCarrying && unit.target?.type?.isResourceDepot == true
    var beingGathered = unit.isBeingGathered
        private set
    val controller = Controller()

    fun update() {
        visible = unit.isVisible
        if (!unit.isVisible) return
        unitType = unit.type
        tilePosition = unit.tilePosition
        x = unit.x
        y = unit.y
        target = unit.target?.let { forUnit(it) }
        flying = unit.isFlying
        carryingGas = unit.isCarryingGas
        carryingMinerals = unit.isCarryingMinerals
        beingGathered = unit.isBeingGathered
        detected = unit.isDetected
        owned = !unit.player.isNeutral
        position = unit.position
        myUnit = unit.player == self
        gatheringGas = unit.isGatheringGas
    }

    fun distanceTo(other: SUnit) = if (other.visible) unit.getDistance(other.unit) else position.getApproxDistance(other.position)
    fun distanceTo(pos: Position) = position.getDistance(pos)
    fun distanceTo(pos: TilePosition) = tilePosition.getDistance(pos)

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

    fun research(tech: TechType) {
        if (sleeping) return
        unit.research(tech)
        sleep()
    }

    fun upgrade(upgrade: UpgradeType) {
        if (sleeping) return
        unit.upgrade(upgrade)
        sleep()
    }

    fun morph(type: UnitType) {
        if (sleeping) return
        unit.morph(type)
        sleep()
    }

    private fun sleep(minFrames: Int = 2) {
        readyOnFrame = Styx.frame + Styx.latencyFrames + max(minFrames, Styx.turnSize)
    }

    fun train(type: UnitType) {
        unit.train(type)
    }

    fun weaponAgainst(other: SUnit): WeaponType = if (other.flying) unitType.airWeapon() else unitType.groundWeapon()
    override fun toString(): String = "$unitType $position [${unit.player.name}]"

    companion object {
        private val units = mutableMapOf<Unit, SUnit>()
        fun forUnit(unit: Unit) = units.computeIfAbsent(unit) { SUnit(it) }
    }
}

class Controller {
    fun reset() {

    }
}