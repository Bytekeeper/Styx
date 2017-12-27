package org.fttbot.estimation

import bwapi.Position
import bwapi.TilePosition
import org.fttbot.FTTBot
import org.fttbot.import.FUnitType
import org.fttbot.layer.FUnit
import org.fttbot.layer.UnitLike
import kotlin.math.max


object EnemyModel {
    private val mapOfSeenUnits: MutableMap<FUnit, SeenUnit> = HashMap()
    var enemyBase: Position? = null
    val seenUnits get() = mapOfSeenUnits.values

    fun updateUnit(unit: FUnit) {
        mapOfSeenUnits.computeIfAbsent(unit) { SeenUnit(unit) }.update()
    }

    fun step() {
        mapOfSeenUnits.values.forEach { it.update() }
    }

    fun guessActualUnits(): Map<FUnitType, Int> = mapOfSeenUnits.values.groupBy { it.type }.mapValues { it.value.size }

    fun remove(fUnit: FUnit) = mapOfSeenUnits.remove(fUnit)
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