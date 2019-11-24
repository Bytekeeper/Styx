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
import org.styx.Styx.frame
import org.styx.Styx.game
import org.styx.Styx.self
import kotlin.math.abs
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
    var vx = 0.0
        private set
    var vy = 0.0
        private set
    val left get() = x - unitType.dimensionLeft()
    val top get() = y - unitType.dimensionUp()
    val right get() = x + unitType.dimensionRight()
    val bottom get() = y + unitType.dimensionDown()
    var angle = 0.0
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
    val walkPosition get() = position.toWalkPosition()
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
    val ready get() = readyOnFrame < frame
    val sleeping get() = readyOnFrame >= frame || isOnCooldown() || !interuptible
    var target: SUnit? = null
        private set
    var orderTarget: SUnit? = null
        private set
    val returningResource get() = unit.isCarrying && orderTarget?.unitType?.isResourceDepot == true
    var beingGathered = false
        private set
    var morphing = false
        private set

    val controller = Controller()
    private lateinit var lastAgent: Agent
    val firstSeenFrame = frame
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
    var remainingTrainTime = 0
        private set
    val isAttacking get() = exists && unit.isAttacking
    val interuptible get() = unit.isInterruptible
    val idle get() = unit.isIdle
    var hasPower: Boolean = false
        private set

    private var stuckFrames = 0
        private set
    var lastUnstickingCommandFrame = Int.MAX_VALUE
        private set

    fun predictPosition(frames: Int) = position + velocity.multiply(frames.toDouble()).toPosition()

    fun update() {
        visible = unit.isVisible
        exists = unit.exists()
        if (!unit.isVisible && frame > 0) return
        lastAgent = agentFactory.of(unit).setUserObject(this)
        lastSeenFrame = game.frameCount
        unitType = unit.type
        tilePosition = unit.tilePosition
        x = unit.x
        y = unit.y
        vx = unit.velocityX
        vy = unit.velocityY
        angle = unit.angle
        target = unit.target?.let { forUnit(it) }
        orderTarget = unit.orderTarget?.let { forUnit(it) }
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
        remainingTrainTime = unit.remainingTrainTime
        enemyUnit = player.isEnemy(self)
        myUnit = player == self
        stimTimer = unit.stimTimer
        morphing = unit.isMorphing
        hasPower = unit.isPowered

        if (frame - lastUnstickingCommandFrame > game.latencyFrames) {
            if (vx == 0.0 && vy == 0.0 && !isOnCooldown() && !gathering) {
                stuckFrames++
                if (stuckFrames > 24) {
                    lastUnstickingCommandFrame = Int.MAX_VALUE
                    stop()
                }
            } else
                lastUnstickingCommandFrame = Int.MAX_VALUE
        } else {
            stuckFrames = 0
        }
    }

    fun distanceTo(target: Position) : Int {
        // compute x distance
        var xDist = left - target.x - 1
        if (xDist < 0) {
            xDist = target.x - right - 1
            if (xDist < 0) {
                xDist = 0
            }
        }

        // compute y distance
        var yDist = this.top - target.y - 1
        if (yDist < 0) {
            yDist = target.y - bottom - 1
            if (yDist < 0) {
                yDist = 0
            }
        }

        // compute actual distance
        return Position.Origin.getApproxDistance(Position(xDist, yDist))
    }

    fun distanceTo(pos: TilePosition) = tilePosition.getDistance(pos)
    fun framesToTravelTo(pos: Position) =
            framesToTurnTo(pos) +
                    (if (flying) (distanceTo(pos) / topSpeed).toInt()
                    else (Styx.map.getPathLength(position, pos) / topSpeed).toInt()) * 4 / 3 + 24

    fun distanceTo(target: SUnit): Int {
        // If target is the same as the source
        if (this === target) {
            return 0
        }

        // compute x distance
        var xDist = left - target.right - 1
        if (xDist < 0) {
            xDist = target.left - right - 1
            if (xDist < 0) {
                xDist = 0
            }
        }

        // compute y distance
        var yDist = top - target.bottom - 1
        if (yDist < 0) {
            yDist = target.top - bottom - 1
            if (yDist < 0) {
                yDist = 0
            }
        }

        // compute actual distance
        return Position.Origin.getApproxDistance(Position(xDist, yDist))
    }

    val tileGeometry: Geometry
        get() {
            val endpos = tilePosition + unitType.tileSize()
            return geometryFactory.toGeometry(Envelope(Coordinate(tilePosition.x.toDouble(), tilePosition.y.toDouble()),
                    Coordinate(endpos.x.toDouble(), endpos.y.toDouble())))
        }

    fun moveTo(target: Position) {
        if (sleeping || unit.position == target || unit.targetPosition == target) return
        lastUnstickingCommandFrame = frame
        unit.move(target)
        sleep()
    }

    fun follow(other: SUnit) {
        if (sleeping || orderTarget == other) return
        lastUnstickingCommandFrame = frame
        unit.follow(other.unit)
        sleep()
    }

    fun build(type: UnitType, at: TilePosition) {
        if (sleeping) return
        lastUnstickingCommandFrame = frame
        unit.build(type, at)
        sleep()
    }

    fun attack(other: SUnit) {
        if (sleeping) return
        lastUnstickingCommandFrame = frame
        if (target == other) return
        unit.attack(other.unit)
        sleep(BWMirrorUnitInfo.stopFrames(unitType))
    }

    fun attack(target: Position) {
        if (sleeping) return
        lastUnstickingCommandFrame = frame
        if (this.orderTarget?.enemyUnit == true)
            return
        unit.attack(target)
        sleep(BWMirrorUnitInfo.stopFrames(unitType))
    }

    fun gather(resource: SUnit) {
        if (sleeping || unit.orderTarget == resource.unit) return
        lastUnstickingCommandFrame = frame
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

    fun cancelMorph() {
        if (sleeping) return
        unit.cancelMorph()
        sleep()
    }

    private fun sleep(minFrames: Int = 2) {
        readyOnFrame = frame + max(minFrames, Styx.turnSize)
    }

    fun train(type: UnitType) {
        if (sleeping) return
        unit.train(type)
        sleep()
    }

    fun rightClick(target: SUnit) {
        if (sleeping) return
        lastUnstickingCommandFrame = frame
        unit.rightClick(target.unit)
        sleep()
    }

    fun stop() {
        if (!unit.isMoving) return
        unit.stop();
    }

    val threats by LazyOnFrame { Styx.units.enemy.inRadius(this, 400) { it.inAttackRange(this, 96f) } }
    fun inAttackRange(other: SUnit, allowance: Float = 0f) = hasWeaponAgainst(other) && maxRangeVs(other) + allowance >= distanceTo(other)

    fun maxRangeVs(other: SUnit) =
            player.weaponMaxRange(weaponAgainst(other)) +
                    if (unitType == UnitType.Terran_Bunker) 64 else 0

    fun hasWeaponAgainst(other: SUnit) = weaponAgainst(other) != WeaponType.None

    fun weaponAgainst(other: SUnit): WeaponType =
            if (unitType == UnitType.Terran_Bunker)
                UnitType.Terran_Marine.groundWeapon()
            else if (other.flying) unitType.airWeapon() else unitType.groundWeapon()

    fun damagePerFrameVs(other: SUnit): Double {
        val (weapon, hits) =
                if (other.flying) {
                    unitType.airWeapon() to unitType.maxAirHits()
                } else {
                    unitType.groundWeapon() to unitType.maxGroundHits()
                }
        if (weapon.damageAmount() == 0)
            return 0.0
        val damagePerHit = DamageCalculator.damageToHitpoints(
                player.damage(weapon),
                hits,
                weapon.damageType(),
                other.player.armor(other.unitType),
                other.unitType.size())
        return damagePerHit / weapon.damageCooldown().toDouble()
    }

    fun isOnCooldown() = unitType.groundWeapon() != WeaponType.None && unitType.groundWeapon().damageCooldown() - unit.groundWeaponCooldown < stopFrames
            || unitType.airWeapon() != WeaponType.None && unitType.airWeapon().damageCooldown() - unit.airWeaponCooldown < stopFrames

    override fun toString(): String = "i$id ${unitType.shortName()}${if (!visible && enemyUnit) "(H)" else ""} $position [${player.name.substring(0, 2)}]"

    fun canBuildHere(at: TilePosition, type: UnitType) = game.canBuildHere(at, type, unit)

    fun agent() : Agent = Agent(lastAgent)

    fun dispose() {
        units.remove(unit)
    }

    fun radiansTo(other: SUnit): Double = radiansTo(other.position)
    fun radiansTo(pos: Position): Double = position.toVector2D().angleTo(pos.toVector2D())
    fun framesToTurnTo(other: SUnit) = (abs((radiansTo(other) - angle).normalizedRadians) * 256.0 / PI2 / unitType.turnRadius()).toInt()
    fun framesToTurnTo(pos: Position) = (abs((radiansTo(pos) - angle).normalizedRadians) * 256.0 / PI2 / unitType.turnRadius()).toInt()

    companion object {
        private val units = mutableMapOf<Unit, SUnit>()
        private val geometryFactory = GeometryFactory()
        private val agentFactory = BWMirrorAgentFactory()

        fun forUnit(unit: Unit) = units.computeIfAbsent(unit) { SUnit(it) }
    }
}

fun UnitType.shortName() = toString().substringAfter('_')

class Controller {
    fun reset() {

    }
}