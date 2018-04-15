package org.fttbot

import bwem.CheckMode
import com.badlogic.gdx.math.Vector2
import org.fttbot.info.Cluster
import org.fttbot.info.EnemyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.info.canAttack
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.WalkPosition
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Unit
import org.openbw.bwapi4j.unit.Worker
import kotlin.math.max

object Potential {
    fun joinAttraction(unit: MobileUnit, others: List<Unit>, tolerance: Int = 32): Vector2 {
        val closeInOn = others.filter { it.getDistance(unit) > tolerance }
        val center = closeInOn.map { it.position }.fold(Position(0, 0)) { acc, position -> acc.add(position) }.div(closeInOn.size)
        return center.minus(unit.position).toVector().nor()
    }

    fun addThreatRepulsion(target: Vector2, unit: MobileUnit, scale: Float = 1f) {
        val threats = (UnitQuery.enemyUnits + EnemyInfo.seenUnits).filter { it.canAttack(unit, 150) }
        target.add(
                threats.fold(Vector2()) { acc, playerUnit -> acc.sub(playerUnit.position.toVector()) }
                        .mulAdd(unit.position.toVector(), threats.size.toFloat())
                        .setLength(scale)
        )
    }

    fun collisionRepulsion(unit: MobileUnit): Vector2 {
        val myCluster = Cluster.mobileCombatUnits.firstOrNull { it.units.contains(unit) } ?: return Vector2.Zero
        return myCluster.units.map {
            if (unit == it) Vector2.Zero
            val diff = (unit.position - it.position).toVector()
            val len = diff.len()
            diff.setLength(max(0f, (1f - len / 2 / unit.topSpeed).toFloat()))
        }.reduce(Vector2::add).nor()
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
            target.add(
                    bestPos.subtract(pos).toPosition().toVector().setLength(scale)
            )
        }
    }

    fun addSafeAreaAttraction(target: Vector2, unit: MobileUnit, scale : Float = 1f) {
        val homePath = FTTBot.bwem.getPath(unit.position,
                reallySafePlace() ?: return)
        val targetChoke = homePath.firstOrNull { it.center.toPosition().getDistance(unit.position) > 128 } ?: return
        target.add((targetChoke.center.toPosition() - unit.position).toVector().setLength(scale))
    }

    private fun reallySafePlace(): Position? {
        return UnitQuery.myBases.maxBy {
            UnitQuery.inRadius(it.position, 300).count { it is Worker }
        }?.position
    }

    fun addSafeAreaAttractionDirect(target: Vector2, unit: MobileUnit, scale: Float = 1f) {
        target.add(((reallySafePlace() ?: return) - unit.position).toVector().setLength(scale))
    }
}