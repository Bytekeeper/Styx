package org.fttbot

import com.badlogic.gdx.math.MathUtils.clamp
import com.badlogic.gdx.math.Vector2
import org.fttbot.info.Cluster
import org.fttbot.info.getWeaponAgainst
import org.openbw.bwapi4j.unit.Armed
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import kotlin.math.max

object Potential {
    fun threatsRepulsion(unit: MobileUnit): Vector2 {
        val threatCluster = Cluster.enemyClusters.minBy { unit.getDistance(it.position) } ?: return Vector2.Zero.cpy()
        return threatCluster.units.map { threatRepulsion(unit, it) }.reduce(Vector2::add).nor()
    }

    private fun threatRepulsion(unit: MobileUnit, enemy: PlayerUnit): Vector2 {
        if (enemy !is Armed) return Vector2.Zero.cpy()
        val enemyWeapon = enemy.getWeaponAgainst(unit)
        val scale = clamp((enemyWeapon.type().maxRange() - enemy.getDistance(unit) + 10 * enemy.topSpeed().toFloat()) / enemyWeapon.type().maxRange().toFloat(), 0f, 1f)
        return unit.position.toVector().sub(enemy.position.toVector()).setLength(enemy.getDamageTo(unit) * scale)
    }

    fun collisionRepulsion(unit: MobileUnit) : Vector2 {
        val myCluster  = Cluster.mobileCombatUnits.first { it.units.contains(unit) }
        return myCluster.units.map {
            if (unit == it) Vector2.Zero
            val diff = (unit.position - it.position).toVector()
            val len = diff.len()
            diff.setLength(max(0f, (1f - len / 2 / unit.topSpeed()).toFloat()))
        }.reduce(Vector2::add).nor()
    }
}