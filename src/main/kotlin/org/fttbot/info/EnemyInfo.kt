package org.fttbot.info

import bwem.area.Area
import org.fttbot.FTTBot
import org.fttbot.LazyOnFrame
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.*
import kotlin.math.max

const val DISCARD_HIDDEN_UNITS_AFTER = 480

object EnemyInfo {
    val seenUnits = ArrayList<PlayerUnit>()
    val enemyBases = ArrayList<Cluster<PlayerUnit>>()
    var hasSeenBase = false
    var hasInvisibleUnits = false
    val occupiedAreas by LazyOnFrame<Map<Area, List<PlayerUnit>>> {
        (UnitQuery.enemyUnits + seenUnits).filter { it is Attacker && it !is Worker}
                .groupBy { FTTBot.bwem.getArea(it.tilePosition) }
    }
    var lastFrame: Int = 0
    var nextFrame : Int = 0
    var lastFramePosition: Map<MobileUnit, Position> = emptyMap()
    var currentFramePosition: Map<MobileUnit, Position> = emptyMap()

    fun predictedPositionOf(unit: PlayerUnit, deltaFrames: Int): Position {
        val lastEnemyPos = EnemyInfo.lastFramePosition[unit] ?: return unit.position
        val df = deltaFrames / max(1, (FTTBot.frameCount - lastFrame))
        return unit.position.subtract(lastEnemyPos).multiply(Position(df, df))
                .add(unit.position)
    }

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

    fun onUnitRenegade(unit: PlayerUnit) {
        if (!unit.isEnemyUnit) {
            seenUnits.remove(unit)
        }
    }

    fun reset() {
        seenUnits.clear()
        enemyBases.clear()
        hasSeenBase = false
        hasInvisibleUnits = false
    }

    fun step() {
        enemyBases.clear()
        enemyBases.addAll(Cluster.enemyClusters.filter { it.units.any { it is Building } })
        hasSeenBase = hasSeenBase || !enemyBases.isEmpty()
        if (!hasSeenBase) {
            val unexploredLocations = FTTBot.game.bwMap.startPositions.filter { !FTTBot.game.bwMap.isExplored(it) }
            if (unexploredLocations.size == 1) {
                enemyBases.add(Cluster(unexploredLocations.first().toPosition(), ArrayList()))
            }
        }
        seenUnits.removeIf {
            it is MobileUnit && (it !is SiegeTank || !it.isSieged) &&
                    (FTTBot.frameCount - it.lastSpotted > DISCARD_HIDDEN_UNITS_AFTER) || FTTBot.game.bwMap.isVisible(it.tilePosition)
        }
        lastFramePosition = currentFramePosition
        lastFrame = nextFrame
        nextFrame = FTTBot.frameCount
        currentFramePosition = UnitQuery.enemyUnits.filterIsInstance(MobileUnit::class.java).map { it to it.position }.toMap()
    }
}

