package org.fttbot.estimation

import org.fttbot.FTTBot
import org.openbw.bwapi4j.Bullet
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.BulletType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Burrowable
import org.openbw.bwapi4j.unit.Cloakable
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Unit

const val DISCARD_HIDDEN_UNITS_AFTER = 480

object EnemyModel {
    private val hiddenUnits = HashMap<Bullet, HiddenUnit>()
    private val burrowedOrCloaked = HashMap<Unit, Int>()
    val seenUnits = HashSet<PlayerUnit>()
    var enemyBase: Position? = null

    fun hiddenUnits() = hiddenUnits.values.toList()

    fun onUnitShow(unit: PlayerUnit) {
        hiddenUnits.values.removeIf { (it.type == UnitType.Unknown || unit.isA(it.type)) && unit.position.getDistance(it.position) < TilePosition.SIZE_IN_PIXELS }
        seenUnits.remove(unit)
        burrowedOrCloaked.remove(unit)
    }

    fun onUnitHide(unit: PlayerUnit) {
        if (FTTBot.game.bwMap.isVisible(unit.tilePosition) && (unit is Burrowable || unit is Cloakable)) {
            burrowedOrCloaked.put(unit, FTTBot.game.interactionHandler.frameCount)
        }
        seenUnits.add(unit)
    }

    fun onUnitDestroy(unit: PlayerUnit) {
        seenUnits.remove(unit)
    }

    private fun updateHiddenUnits() {
        hiddenUnits.values.removeIf { FTTBot.frameCount - it.seenOnFrame > DISCARD_HIDDEN_UNITS_AFTER }
        FTTBot.game.bullets.filter { it.type != BulletType.Fusion_Cutter_Hit && it.source == null }
                .forEach { b ->
                    val type = when (b.type) {
                        BulletType.Subterranean_Spines -> UnitType.Zerg_Lurker
                        else -> return@forEach
                    }
                    val position = Position(b.position.x, b.position.y)
                    hiddenUnits.computeIfAbsent(b) { HiddenUnit(type) }.update(position, position.toTilePosition(), FTTBot.game.interactionHandler.frameCount)
                }
    }

    private fun updateBurrowedOrCloakedUnits() {
        burrowedOrCloaked.values.removeIf { FTTBot.frameCount - it > DISCARD_HIDDEN_UNITS_AFTER }
    }

    fun step() {
        updateHiddenUnits()
        updateBurrowedOrCloakedUnits()
        seenUnits.removeIf { !burrowedOrCloaked.containsKey(it) && FTTBot.game.bwMap.isVisible(it.tilePosition) }
    }
}

class HiddenUnit(val type: UnitType) {
    lateinit var position: Position
    lateinit var tilePosition: TilePosition
    var seenOnFrame: Int = 0
    val width: Int = type.width()
    fun update(position: Position, tilePosition: TilePosition, frameCount: Int) {
        this.position = position
        this.tilePosition = tilePosition
        this.seenOnFrame = frameCount
    }

    override fun toString(): String {
        return "$type at $position"
    }
}