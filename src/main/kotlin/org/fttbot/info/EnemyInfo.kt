package org.fttbot.info

import bwem.area.Area
import com.badlogic.gdx.math.Vector2
import org.fttbot.*
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.*

const val DISCARD_HIDDEN_UNITS_AFTER = 800

object EnemyInfo {
    val seenUnits = ArrayList<PlayerUnit>()
    val enemyBases = ArrayList<Cluster<PlayerUnit>>()
    var hasSeenBase = false
    var hasInvisibleUnits = false
    val occupiedAreas by LazyOnFrame<Map<Area, List<PlayerUnit>>> {
        (UnitQuery.enemyUnits + seenUnits).filter { it is Attacker && it !is Worker }
                .groupBy { FTTBot.bwem.getArea(it.tilePosition) }
    }
    var nextFrame: Int = 0
    var currentFramePosition: Map<MobileUnit, Position> = emptyMap()

    fun predictedPositionOf(unit: PlayerUnit, deltaFrames: Double): Position {
        return unit.position.toVector().mulAdd(Vector2(unit.velocityX.toFloat(), unit.velocityY.toFloat()), deltaFrames.toFloat()).toPosition().asValidPosition()
    }

    var lastNewEnemyFrame: Int = 0
        private set

    fun onUnitShow(unit: PlayerUnit) {
        if (unit is Cloakable || unit is Lurker) hasInvisibleUnits = true
        val removed = seenUnits.remove(unit)
        if (!removed) {
            lastNewEnemyFrame = FTTBot.frameCount
        }
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
        enemyBases.addAll(Cluster.clusters.map { it.userObject as Cluster<PlayerUnit> }.filter { it.units.any { it.isEnemyUnit && it is Building } })
        hasSeenBase = hasSeenBase || !enemyBases.isEmpty()
        val unexploredLocations = FTTBot.game.bwMap.startPositions.filter { !FTTBot.game.bwMap.isExplored(it) }
        if (!hasSeenBase && unexploredLocations.size == 1) {
            enemyBases.add(Cluster(unexploredLocations.first().toPosition(), ArrayList()))
        }
//        unexploredLocations.filter {tp ->
//            val position = tp.toPosition()
//            (seenUnits.filter { it.getDistance(position) < 300 } + UnitQuery.enemyUnits.inRadius(position, 300)).any { it is Building }
//        }.map {tp ->
//            object : Worker() {
//                override fun getId(): Int = -1
//                override fun getPosition(): Position = tp.toPosition()
//                override fun getTilePosition(): TilePosition = tp
//                override fun getPlayer(): Player = FTTBot.enemies[0]
//                override fun getType(): UnitType = UnitType.Terran_SCV
//            }
//        }.forEach {
//            seenUnits.add(it)
//        }
        seenUnits.removeIf {
            //            it is MobileUnit && (it !is SiegeTank || !it.isSieged) && (it !is Worker) &&
//                    (FTTBot.frameCount - it.lastSpotted > DISCARD_HIDDEN_UNITS_AFTER) ||
            ((it !is Burrowable || !it.isBurrowed) && FTTBot.game.bwMap.isVisible(it.tilePosition))
        }
        nextFrame = FTTBot.frameCount
        currentFramePosition = UnitQuery.enemyUnits.filterIsInstance(MobileUnit::class.java).map { it to it.position }.toMap()
    }
}

