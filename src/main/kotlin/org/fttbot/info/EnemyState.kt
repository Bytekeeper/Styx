package org.fttbot.info

import org.fttbot.FTTBot
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*

const val DISCARD_HIDDEN_UNITS_AFTER = 480

object EnemyState {
    val seenUnits = HashSet<PlayerUnit>()
    var enemyBase: Position? = null
    var hasInvisibleUnits = false


    fun onUnitShow(unit: PlayerUnit) {
        if (unit is Cloakable || unit is Lurker) hasInvisibleUnits = true
        seenUnits.remove(unit)
    }

    fun onUnitHide(unit: PlayerUnit) {
        seenUnits.add(unit)
    }

    fun onUnitDestroy(unit: PlayerUnit) {
        seenUnits.remove(unit)
    }

    fun step() {
        seenUnits.removeIf {
            (it !is Burrowable || !it.isBurrowed)
                    && (it !is Cloakable || !it.isCloaked)
                    && (it is MobileUnit)
                    && (FTTBot.frameCount - it.lastSpotted > DISCARD_HIDDEN_UNITS_AFTER
                    || FTTBot.game.bwMap.isVisible(it.tilePosition))
        }
    }
}

