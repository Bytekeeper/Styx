package org.fttbot.layer

import bwapi.*
import bwapi.Unit
import org.fttbot.FTTBot
import org.fttbot.approxDistance
import org.fttbot.behavior.BBUnit
import org.fttbot.estimation.EnemyModel
import org.fttbot.estimation.MAX_FRAMES_TO_ATTACK
import org.fttbot.import.FUnitType
import org.fttbot.import.FWeaponType

fun Unit.toFUnit() = FUnit.of(this)

const val MAX_MELEE_RANGE = 64

fun FWeaponType.isMelee() = this != FWeaponType.None &&  maxRange <= MAX_MELEE_RANGE
fun FWeaponType.inRange(distance: Int, safety: Int): Boolean =
        this.minRange <= distance && this.maxRange >= distance - safety
fun FUnitType.getWeaponAgainst(other: UnitLike) = if (other.isAir) airWeapon else groundWeapon


interface UnitLike {
    val position: Position?
    val type: FUnitType
    val tilePosition: TilePosition?
    val isUnderDarkSwarm: Boolean
    val hitPoints: Int
    val isVisible: Boolean
    val left get() = position!!.x - type.dimensionLeft
    val right get() = position!!.x + type.dimensionRight
    val top get() = position!!.y - type.dimensionUp
    val bottom get() = position!!.y + type.dimensionDown
    val groundWeaponCooldown: Int
    val airWeaponCooldown: Int
    val groundHeight get() = FTTBot.game.getGroundHeight(tilePosition!!)
    val canMove get() = type.canMove
    val isAir get() = type.isFlyer

    fun distanceTo(other: UnitLike): Int {
        val left = other.left - 1
        val top = other.top - 1
        val right = other.right + 1
        val bottom = other.bottom + 1

        var xDist = this.left - right
        if (xDist < 0) {
            xDist = left - this.right
            if (xDist < 0) {
                xDist = 0
            }
        }
        var yDist = this.top - bottom
        if (yDist < 0) {
            yDist = top - this.bottom
            if (yDist < 0) {
                yDist = 0
            }
        }
        return approxDistance(xDist, yDist)
    }

    fun distanceTo(position: Position): Int {
        val left = position.x - 1
        val top = position.y - 1
        val right = position.x + 1
        val bottom = position.y + 1

        var xDist = this.left - right
        if (xDist < 0) {
            xDist = left - this.right
            if (xDist < 0) {
                xDist = 0
            }
        }
        var yDist = this.top - bottom
        if (yDist < 0) {
            yDist = top - this.bottom
            if (yDist < 0) {
                yDist = 0
            }
        }
        return approxDistance(xDist, yDist)
    }

    fun canAttackConsideringCooldowns(unit: UnitLike, safety: Int = 0): Boolean {
        val distance = unit.distanceTo(this)
        return (unit.type.isFlyer && type.airWeapon != FWeaponType.None && type.airWeapon.inRange(distance, safety)
                && (groundWeaponCooldown == 0))
                || (!unit.type.isFlyer && type.groundWeapon != FWeaponType.None && type.groundWeapon.inRange(distance, safety)
                && (airWeaponCooldown == 0))
    }


    fun canAttack(unit: UnitLike, safety: Int = 0): Boolean {
        if (position == null) throw IllegalStateException("Position is required for attack check")
        val distance = unit.distanceTo(this)
        val weapon = type.getWeaponAgainst(unit)
        return weapon != FWeaponType.None && weapon.inRange(distance, safety)
    }
}

class FUnit private constructor(val unit: Unit) : UnitLike {
    companion object {
        private val units = HashMap<Int, FUnit>()

        fun of(unit: Unit): FUnit = units.computeIfAbsent(unit.id) { FUnit(unit) }
        fun destroy(unit: Unit) = units.remove(unit.id)

        fun allUnits() = FTTBot.game.allUnits.map { FUnit.of(it) }
        fun unitsInRadius(position: Position, radius: Int) = FTTBot.game.getUnitsInRadius(position, radius).map { FUnit.of(it) }
        fun minerals() = FTTBot.game.minerals.map { FUnit.of(it) }
        fun neutrals() = FTTBot.game.neutralUnits.map { FUnit.of(it) }
        fun myUnits() = FTTBot.self.units.filter { it.player == FTTBot.self }.map { FUnit.of(it) }
        fun enemyUnits() = FTTBot.enemy.units.filter { it.player == FTTBot.enemy }.map { FUnit.of(it) }
        fun myBases() = myUnits().filter { it.isBase }
        fun myWorkers() = FTTBot.game.allUnits.filter { it.type.isWorker && it.player == FTTBot.self && it.isCompleted }.map { FUnit.of(it) }
    }

    fun closest(others: Iterable<FUnit>): FUnit? {
        return others.minBy { it.distanceTo(this) }
    }

    val isStartingAttack get() = unit.isStartingAttack
    val board get() = BBUnit.of(this)
    val isWorker: Boolean get() = type.isWorker
    val isGatheringMinerals: Boolean get() = unit.isGatheringMinerals
    val isGatheringGas get() = unit.isGatheringGas
    val isConstructing get() = unit.isConstructing
    val buildUnit get() = unit.buildUnit?.toFUnit()
    val buildType get() = FUnitType.of(unit.buildType)
    val isIdle get() = unit.isIdle
    override val position: Position get() = unit.position
    val isMineralField: Boolean get() = unit.type.isMineralField
    val isRefinery get() = unit.type.isRefinery
    val isPlayerOwned: Boolean get() = unit.player == FTTBot.self
    val isEnemy get() = unit.player == FTTBot.game.enemy()
    override val type: FUnitType get() = FUnitType.of(unit.type)
    val isCarryingGas get() = unit.isCarryingGas
    val isCarryingMinerals get() = unit.isCarryingMinerals
    val isAttackFrame get() = unit.isAttackFrame
    val isBeingGathered get() = unit.isBeingGathered
    val isBase
        get() = when (unit.type) {
            UnitType.Terran_Command_Center -> true
            UnitType.Protoss_Nexus -> true
            UnitType.Zerg_Hatchery -> true
            UnitType.Zerg_Lair -> true
            UnitType.Zerg_Hive -> true
            else -> false
        }
    val energy get() = unit.energy
    val isTraining: Boolean get() = unit.buildUnit != null || !unit.trainingQueue.isEmpty()
    val isBuilding get() = unit.type.isBuilding
    val isMoving: Boolean get() = unit.isMoving
    val isCompleted get() = unit.isCompleted
    val initialTilePosition get() = unit.initialTilePosition
    override val tilePosition get() = unit.tilePosition
    val targetPosition get() = unit.targetPosition
    val canAttack get() = unit.type.canAttack() || type == FUnitType.Terran_Bunker
    val isAttacking get() = unit.isAttacking
    override val isVisible get() = unit.isVisible
    val canHeal get() = type == FUnitType.Terran_Medic
    val isMelee get() = type.groundWeapon.isMelee()
    val target get() = unit.orderTarget?.toFUnit()

    val isDead get() = !unit.exists() && unit.isVisible
    override val hitPoints get() = unit.hitPoints
    override val canMove get() = unit.canMove()
    override val groundWeaponCooldown get() = unit.groundWeaponCooldown
    override val airWeaponCooldown get() = unit.airWeaponCooldown
    val groundWeapon get() = FWeaponType.of(unit.type.groundWeapon())
    val airWeapon get() = FWeaponType.of(unit.type.airWeapon())
    val isInRefinery get() = unit.isGatheringGas && !unit.isInterruptible
    override val isUnderDarkSwarm get() = unit.isUnderDarkSwarm

    fun lastCommand() = unit.lastCommand

    fun canMake(type: FUnitType) = FTTBot.game.canMake(type.source, unit)
    fun distanceInTilesTo(position: TilePosition) = position.getDistance(tilePosition)
    fun gather(target: FUnit) = unit.gather(target.unit)
    fun returnCargo() = if (unit.lastCommand.unitCommandType != UnitCommandType.Return_Cargo) unit.returnCargo() else true
    fun attack(targetUnit: FUnit) = if (unit.lastCommand.unitCommandType != UnitCommandType.Attack_Unit || unit.orderTarget != targetUnit.unit) unit.attack(targetUnit.unit) else true
    fun attack(targetPosition: Position) = unit.attack(targetPosition)

    fun construct(type: FUnitType, position: TilePosition) = unit.build(type.source, position)
    fun rightClick(target: FUnit) = unit.rightClick(target.unit)

    fun move(position: TilePosition) = unit.move(position.toPosition())
    fun move(position: Position) = unit.move(position)

    fun train(type: FUnitType) = unit.train(type.source)

    fun isInWeaponRange(other: FUnit) = unit.isInWeaponRange(other.unit)

    override fun toString(): String = "${if (isPlayerOwned) "My" else if (isEnemy) "Enemy" else "Neutral"} ${unit.type.toString().substringAfter('_')} at ${unit.position}"

    fun stopConstruct() = unit.haltConstruction()

    fun potentialAttackers(): List<UnitLike> =
            (FUnit.unitsInRadius(unit.position, 300)
                    .filter { it.isEnemy && it.canAttack(this, (it.type.topSpeed * MAX_FRAMES_TO_ATTACK).toInt()) } +
                    EnemyModel.seenUnits.filter { !it.isVisible && it.position != null && it.canAttack(this, (it.type.topSpeed * MAX_FRAMES_TO_ATTACK).toInt()) })

    fun canBeAttacked() = !potentialAttackers().isEmpty()

}
