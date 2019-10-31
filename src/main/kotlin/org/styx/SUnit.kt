package org.styx

import bwapi.*
import bwapi.Unit
import org.bk.ass.info.BWMirrorUnitInfo
import org.bk.ass.sim.Agent
import org.bk.ass.sim.BWMirrorAgentFactory
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.Envelope
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.math.Vector2D
import org.styx.Styx.game
import org.styx.Styx.self
import kotlin.math.max

class SUnit private constructor(val unit: Unit) {
    private var readyOnFrame = 0
    private var stimTimer: Int = 0
    var shields: Int = 0
        private set
    var hitPoints: Int = 0
        private set
    var x = 0
        private set
    var y = 0
        private set
    var moving = false
        private set
    var visible = false
        private set
    var exists = false
        private set
    var isCompleted = false
        private set
    var detected = false
        private set
    val initialTilePosition = unit.initialTilePosition
    lateinit var tilePosition: TilePosition
        private set
    lateinit var position: Position
        private set
    val id: Int = unit.id
    var myUnit = false
    var enemyUnit: Boolean = false
    lateinit var buildType: UnitType
        private set
    var unitType: UnitType = unit.type
        private set
    var flying = false
        private set
    var carryingGas = false
        private set
    var carryingMinerals = false
        private set
    var gatheringGas = false
        private set
    var gatheringMinerals = false
        private set
    val gathering get() = gatheringGas || gatheringMinerals
    val carrying: Boolean get() = carryingGas || carryingMinerals
    val owned get() = !player.isNeutral
    val ready get() = readyOnFrame < Styx.frame
    val sleeping get() = readyOnFrame >= Styx.frame
    var target: SUnit? = null
        private set
    val returningMinerals get() = unit.isCarrying && unit.target?.type?.isResourceDepot == true
    var beingGathered = false
        private set

    val controller = Controller()
    private lateinit var lastAgent: Agent
    var lastSeenFrame = 0
        private set
    private var lastDamageFrame: Int? = null
    lateinit var player: Player
        private set
    lateinit var velocity: Vector2D
        private set
    var topSpeed: Double = 0.0
        private set
    val stopFrames = BWMirrorUnitInfo.stopFrames(unitType)
    var remainingBuildTime = 0
        private set

    fun update() {
        visible = unit.isVisible
        if (!unit.isVisible && Styx.frame > 0) return
        lastAgent = agentFactory.of(unit).setUserObject(this)
        lastSeenFrame = game.frameCount
        exists = unit.exists()
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
        position = unit.position
        gatheringGas = unit.isGatheringGas
        gatheringMinerals = unit.isGatheringMinerals
        isCompleted = unit.isCompleted
        buildType = unit.buildType
        if (unit.hitPoints < hitPoints) {
            lastDamageFrame = game.frameCount
        }
        hitPoints = unit.hitPoints
        shields = unit.shields
        moving = unit.isMoving
        player = unit.player
        velocity = Vector2D(unit.velocityX, unit.velocityY)
        topSpeed = player.topSpeed(unitType)
        remainingBuildTime = unit.remainingBuildTime
        enemyUnit = player.isEnemy(self)
        myUnit = player == self
        stimTimer = unit.stimTimer
    }

    fun distanceTo(other: SUnit) = if (other.visible) unit.getDistance(other.unit) else position.getDistance(other.position).toInt()
    fun distanceTo(pos: Position) = position.getDistance(pos)
    fun distanceTo(pos: TilePosition) = tilePosition.getDistance(pos)
    fun framesToTravelTo(pos: Position) =
            (if (flying) (distanceTo(pos) / topSpeed).toInt()
            else (Styx.map.getPathLength(position, pos) / topSpeed).toInt()) + 12

    val tileGeometry: Geometry
        get() {
            val endpos = tilePosition + unitType.tileSize()
            return geometryFactory.toGeometry(Envelope(Coordinate(tilePosition.x.toDouble(), tilePosition.y.toDouble()),
                    Coordinate(endpos.x.toDouble(), endpos.y.toDouble())))
        }

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
        if (target == other) return
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
        if (sleeping) return
        unit.train(type)
        sleep()
    }

    fun rightClick(target: SUnit) {
        if (sleeping) return
        unit.rightClick(target.unit)
        sleep()
    }

    fun stop() {
        unit.stop();
    }

    fun inAttackRange(other: SUnit, allowance: Float = 0f) = maxRangeVs(other) + allowance >= distanceTo(other)
    fun maxRangeVs(other: SUnit) =
            player.weaponMaxRange(weaponAgainst(other)) +
                    if (unitType == UnitType.Terran_Bunker) 64 else 0

    fun weaponAgainst(other: SUnit): WeaponType =
            if (unitType == UnitType.Terran_Bunker)
                UnitType.Terran_Marine.groundWeapon()
            else if (other.flying) unitType.airWeapon() else unitType.groundWeapon()
    fun damagePerFrameVs(other: SUnit) =
            (if (other.flying)
                (unitType.airWeapon().damageAmount() * unitType.maxAirHits()) / unitType.airWeapon().damageCooldown().toDouble()
            else
                (unitType.groundWeapon().damageAmount() * unitType.maxGroundHits()) / unitType.groundWeapon().damageCooldown().toDouble()).orZero()

    fun isOnCooldown() = unit.groundWeaponCooldown > 0 && unitType.groundWeapon().damageCooldown() - unit.groundWeaponCooldown < stopFrames
            || unit.airWeaponCooldown > 0 && unitType.airWeapon().damageCooldown() - unit.airWeaponCooldown < stopFrames

    override fun toString(): String = "$unitType $position [${player.name}]"

    fun canBuildHere(at: TilePosition, type: UnitType) = game.canBuildHere(at, type, unit)

    fun agent() = Agent(lastAgent)
            .setCooldown(remainingBuildTime) // Trick to prevent incomplete units from attacking

    fun dispose() {
        units.remove(unit)
    }

    companion object {
        private val units = mutableMapOf<Unit, SUnit>()
        private val geometryFactory = GeometryFactory()
        private val agentFactory = BWMirrorAgentFactory()

        fun forUnit(unit: Unit) = units.computeIfAbsent(unit) { SUnit(it) }
    }
}

class Controller {
    fun reset() {

    }
}