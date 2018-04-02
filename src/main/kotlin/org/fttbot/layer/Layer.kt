package org.fttbot.layer

import org.fttbot.FTTBot
import org.fttbot.LazyOnFrame
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.WalkPosition
import org.openbw.bwapi4j.type.UnitType

interface Layer {
    fun eval(pos: WalkPosition) : Double;
}

object WalkableLayer : Layer {
    override fun eval(pos: WalkPosition): Double {
        val miniTile = FTTBot.bwem.data.getMiniTile(pos)
        if (!miniTile.isWalkable)
            return 1.0
        return 1.0 / miniTile.altitude.intValue().toDouble()
    }
}

object UnexploredLayer : Layer {
    override fun eval(pos: WalkPosition): Double {
        if (FTTBot.game.bwMap.isExplored(pos.toTilePosition())) {
            return 0.0
        }
        return 1.0
    }
}

class DangerRepulsion(type: UnitType) : Layer {
    override fun eval(pos: WalkPosition): Double {
//        UnitQuery.enemyUnits.filter {
//        }
        return 0.0
    }

    companion object {
        private val perUnitType = HashMap<UnitType, Lazy<Layer>>()

        fun forUnit(type: UnitType) : Layer {
            return perUnitType.computeIfAbsent(type) {
                LazyOnFrame {
                    DangerRepulsion(type)
                }
            }.value
        }
    }
}