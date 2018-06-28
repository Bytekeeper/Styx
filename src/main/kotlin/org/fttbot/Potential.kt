package org.fttbot

import bwem.CheckMode
import com.badlogic.gdx.math.Vector2
import org.fttbot.info.EnemyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.info.canAttack
import org.fttbot.info.inRadius
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.WalkPosition
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Unit
import org.openbw.bwapi4j.unit.Worker
import kotlin.math.min

object Potential {
    fun joinAttraction(unit: MobileUnit, others: List<Unit>, tolerance: Int = 32): Vector2 {
        val closeInOn = others.filter { it.getDistance(unit) > tolerance }
        val center = closeInOn.map { it.position }.fold(Position(0, 0)) { acc, position -> acc.add(position) }.div(closeInOn.size)
        return center.minus(unit.position).toVector().nor()
    }

    fun addThreatRepulsion(target: Vector2, unit: MobileUnit, avoidRange: Int? = null, scale: Float = 1f) {
        val threats = (UnitQuery.enemyUnits + EnemyInfo.seenUnits).filter { it.canAttack(unit, avoidRange ?: 48) }
        target.add(
                threats.fold(Vector2()) { acc, playerUnit -> acc.sub(playerUnit.position.toVector()) }
                        .mulAdd(unit.position.toVector(), threats.size.toFloat())
                        .setLength(scale)
        )
    }

    fun addCollisionRepulsion(target: Vector2, unit: MobileUnit, scale: Float = 1f) {
        val relevantUnits = UnitQuery.allUnits
                .filter { it != unit && !it.isFlying && it.getDistance(unit) < 48 }
        val force = unit.position.toVector().scl(relevantUnits.size.toFloat())
        relevantUnits.forEach { force.sub(it.position.toVector()) }
        target.add(force.setLength(scale))
    }

    fun addWallRepulsion(target: Vector2, unit: MobileUnit, scale: Float = 1f) {
        val pos = unit.position.toWalkPosition()
        var bestAltitude = 0
        var bestPos: WalkPosition? = null
        for (i in -3..3) {
            for (j in -3..3) {
                val wp = WalkPosition(pos.x + i * 2, pos.y + j * 2)
                if ((i != 0 || j != 0) && FTTBot.game.bwMap.isValidPosition(wp)) {
                    val miniTile = FTTBot.bwem.data.getMiniTile(wp, CheckMode.NO_CHECK)
                    val altitude = miniTile.altitude
                    if (altitude.intValue() > bestAltitude) {
                        bestAltitude = altitude.intValue()
                        bestPos = wp
                    }
                }
            }
        }
        if (bestPos != null) {
            val tmp = bestPos.subtract(pos).toPosition().toVector()
            tmp.setLength((scale * min(1.0, 32.0 / bestAltitude)).toFloat())
            target.add(tmp)
        }
    }

    fun addWallAttraction(target: Vector2, unit: MobileUnit, scale: Float = 1f) {
        val pos = unit.position.toWalkPosition()
        var bestAltitude = Int.MAX_VALUE
        var bestPos: WalkPosition? = null
        for (i in -3..3) {
            for (j in -3..3) {
                val wp = WalkPosition(pos.x + i * 2, pos.y + j * 2)
                if ((i != 0 || j != 0) && FTTBot.game.bwMap.isValidPosition(wp)) {
                    val miniTile = FTTBot.bwem.data.getMiniTile(wp, CheckMode.NO_CHECK)
                    val altitude = miniTile.altitude
                    if (altitude.intValue() < bestAltitude) {
                        bestAltitude = altitude.intValue()
                        bestPos = wp
                    }
                }
            }
        }
        if (bestPos != null) {
            val tmp = bestPos.subtract(pos).toPosition().toVector()
            tmp.setLength((scale * (1.0 - min(1.0, bestAltitude / 92.0))).toFloat())
            target.add(tmp)
        }
    }

    fun addSafeAreaAttraction(target: Vector2, unit: MobileUnit, scale: Float = 1f) {
        val home = reallySafePlace()
        val homePath = FTTBot.bwem.getPath(unit.position,
                home ?: return)
        if (home.getDistance(unit.position) < 64) return
        val waypoint =
                homePath.firstOrNull { it.center.toPosition().getDistance(unit.position) >= 4 * TilePosition.SIZE_IN_PIXELS }?.center?.toPosition()
                        ?: home
                        ?: return
        target.add((waypoint - unit.position).toVector().setLength(scale))
    }

    private fun reallySafePlace(): Position? {
        return UnitQuery.myBases
                .filter { UnitQuery.enemyUnits.inRadius(it.position, 300).count { it is Attacker && it !is Worker } < 3 }
                .maxBy {
                    UnitQuery.inRadius(it.position, 300).count { it is Worker }
                }?.position
    }

    fun addSafeAreaAttractionDirect(target: Vector2, unit: MobileUnit, scale: Float = 1f) {
        target.add(((reallySafePlace() ?: return) - unit.position).toVector().setLength(scale))
    }
}