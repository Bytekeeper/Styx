package org.fttbot

import bwem.check_t
import bwem.tile.MiniTile
import com.badlogic.gdx.math.MathUtils.clamp
import com.badlogic.gdx.math.Vector2
import com.sun.javafx.animation.TickCalculation.sub
import org.fttbot.info.Cluster
import org.fttbot.info.UnitQuery
import org.fttbot.info.UnitQuery.myUnits
import org.fttbot.info.getWeaponAgainst
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.WalkPosition
import org.openbw.bwapi4j.type.Color
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.Armed
import org.openbw.bwapi4j.unit.Bunker
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import kotlin.math.max
import kotlin.math.min

object Potential {
    fun threatsRepulsion(unit: MobileUnit): Vector2 {
        val threatCluster = Cluster.enemyClusters.minBy { unit.getDistance(it.position) } ?: return Vector2.Zero.cpy()
        return threatCluster.units.map { threatRepulsion(unit, it) }.reduce(Vector2::add).nor()
    }

    private fun threatRepulsion(unit: MobileUnit, enemy: PlayerUnit): Vector2 {
        if (enemy !is Armed) return Vector2.Zero.cpy()
        val enemyWeaponType = if (unit is Bunker) WeaponType.Gauss_Rifle else enemy.getWeaponAgainst(unit).type()
        val scale = clamp((enemyWeaponType.maxRange() - enemy.getDistance(unit) + 10 * enemy.topSpeed().toFloat()) / enemyWeaponType.maxRange().toFloat(), 0f, 1f)
        return unit.position.toVector().sub(enemy.position.toVector()).setLength(enemy.getDamageTo(unit) * scale)
    }

    fun collisionRepulsion(unit: MobileUnit) : Vector2 {
        val myCluster  = Cluster.mobileCombatUnits.firstOrNull { it.units.contains(unit) } ?: return Vector2.Zero
        return myCluster.units.map {
            if (unit == it) Vector2.Zero
            val diff = (unit.position - it.position).toVector()
            val len = diff.len()
            diff.setLength(max(0f, (1f - len / 2 / unit.topSpeed()).toFloat()))
        }.reduce(Vector2::add).nor()
    }

    fun wallRepulsion(unit: MobileUnit) : Vector2 {
        val result = Vector2()
        val pos = unit.position.toWalkPosition()
        var bestAltitude = 0
        var bestPos : WalkPosition? = null
        for (i in -3..3 ) {
            for (j in -3..3) {
                val wp = WalkPosition(pos.x + i * 2, pos.y + j * 2)
                if (i != 0 || j != 0) {
                    val miniTile = FTTBot.bwem.GetMap().data.getMiniTile(wp, check_t.no_check)
                    val altitude = miniTile.altitude
                    if (altitude.intValue() > bestAltitude) {
                        bestAltitude = altitude.intValue()
                        bestPos = wp
                    }
                }
            }
        }
        return if (bestPos == null) return Vector2.Zero else bestPos.subtract(pos).toPosition().toVector().nor()
    }

    fun safeAreaAttraction(unit: MobileUnit) : Vector2 {
        val map = FTTBot.bwem.GetMap()
        val homePath = map.GetPath(unit.position, UnitQuery.myBases.firstOrNull()?.position ?: return Vector2.Zero)
        val targetChoke = if (homePath.isEmpty) return Vector2.Zero else homePath[0]
        return (targetChoke.Center().toPosition() - unit.position).toVector().nor()
    }
}