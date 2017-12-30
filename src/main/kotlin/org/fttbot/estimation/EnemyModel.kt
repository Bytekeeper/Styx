package org.fttbot.estimation

import org.fttbot.FTTBot
import org.openbw.bwapi4j.Bullet
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.BulletType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Unit

const val DISCARD_HIDDEN_UNITS_AFTER = 480

object EnemyModel {
    val hiddenUnits = HashMap<Bullet, HiddenUnit>()
    var enemyBase: Position? = null

    fun updateUnit(unit: Unit) {
        hiddenUnits.values.removeIf { (it.type == UnitType.Unknown || unit.isA(it.type)) && unit.position.getDistance(it.position) < TilePosition.SIZE_IN_PIXELS}
    }

    private fun updateHiddenUnits() {
        hiddenUnits.values.removeIf { FTTBot.game.interactionHandler.frameCount - it.seenOnFrame > DISCARD_HIDDEN_UNITS_AFTER}
        FTTBot.game.bullets.filter { it.source == null }
                .forEach { b ->
                    val type = when (b.type) {
                        else -> return@forEach
                    }
                    val position = Position(b.position.x, b.position.y)
                    val hiddenUnit = HiddenUnit(type, position, position.toTilePosition(), FTTBot.game.interactionHandler.frameCount)

                    hiddenUnits.put(b, hiddenUnit)
                }
    }

    fun step() {
        updateHiddenUnits()
    }
}

class HiddenUnit(val type: UnitType, val position: Position, val tilePosition: TilePosition, val seenOnFrame: Int) {
    val isUnderDarkSwarm: Boolean = false
    val hitPoints: Int = type.maxHitPoints()
    val isVisible: Boolean = false
    var groundWeaponCooldown: Int = 0
    var airWeaponCooldown: Int = 0
}