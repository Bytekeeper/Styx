package org.fttbot.estimation

import bwapi.Bullet
import bwapi.Position
import bwapi.TilePosition
import org.fttbot.FTTBot
import org.fttbot.import.FBulletType
import org.fttbot.import.FUnitType
import org.fttbot.layer.FUnit
import org.fttbot.layer.UnitLike
import kotlin.math.max

const val DISCARD_HIDDEN_UNITS_AFTER = 480

object EnemyModel {
    private val mapOfSeenUnits: MutableMap<FUnit, SeenUnit> = HashMap()
    val hiddenUnits = HashMap<Bullet, HiddenUnit>()
    var enemyBase: Position? = null
    val seenUnits get() = mapOfSeenUnits.values

    fun updateUnit(unit: FUnit) {
        mapOfSeenUnits.computeIfAbsent(unit) { SeenUnit(unit) }.update()
        hiddenUnits.values.removeIf { unit.type == it.type && unit.position.getDistance(it.position) < TilePosition.SIZE_IN_PIXELS}
    }

    private fun updateHiddenUnits() {
        hiddenUnits.values.removeIf { FTTBot.game.frameCount - it.seenOnFrame > DISCARD_HIDDEN_UNITS_AFTER}
        FTTBot.game.bullets.filter { it.source == null && FBulletType.of(it.type) != null }
                .forEach { b ->
                    val type = when (FBulletType.of(b.type)) {
                        else -> return@forEach
                    }
                    val position = Position(b.position.x, b.position.y)
                    val hiddenUnit = HiddenUnit(type, position, position.toTilePosition(), FTTBot.game.frameCount)

                    hiddenUnits.put(b, hiddenUnit)
                }
    }

    fun step() {
        mapOfSeenUnits.values.forEach { it.update() }
        updateHiddenUnits()
    }

    fun guessActualUnits(): Map<FUnitType, Int> = mapOfSeenUnits.values.groupBy { it.type }.mapValues { it.value.size }

    fun remove(fUnit: FUnit) = mapOfSeenUnits.remove(fUnit)
}

class HiddenUnit(override val type: FUnitType, override val position: Position, override val tilePosition: TilePosition, val seenOnFrame: Int) : UnitLike {
    override val isUnderDarkSwarm: Boolean = false
    override val hitPoints: Int = type.maxHitPoints
    override val isVisible: Boolean = false
    override var groundWeaponCooldown: Int = 0
    override var airWeaponCooldown: Int = 0
}

class SeenUnit(val unit: FUnit) : UnitLike {
    override lateinit var type: FUnitType
    override var position: Position? = null
    override var tilePosition: TilePosition? = null
    override var hitPoints: Int = 0
    override var isUnderDarkSwarm: Boolean = false
    override var isVisible: Boolean = false
    override var airWeaponCooldown: Int = 0
    override var groundWeaponCooldown: Int = 0

    internal fun update() {
        isVisible = unit.isVisible
        airWeaponCooldown = max(0, airWeaponCooldown - 1)
        groundWeaponCooldown = max(0, groundWeaponCooldown - 1)
        if (unit.isVisible) {
            type = unit.type
            position = unit.position
            tilePosition = unit.tilePosition
            hitPoints = unit.hitPoints
            airWeaponCooldown = unit.airWeaponCooldown
            groundWeaponCooldown = unit.groundWeaponCooldown
        } else if (tilePosition != null && FTTBot.game.isVisible(tilePosition)) {
            tilePosition = null
            position = null
        }
    }
}