package org.styx.micro

import bwapi.Position
import bwapi.UnitType
import bwapi.WeaponType
import org.locationtech.jts.math.DD
import org.locationtech.jts.math.Vector2D
import org.styx.*
import org.styx.Styx.map
import org.styx.Styx.units
import kotlin.math.max
import kotlin.math.min

object Potential {
    fun groundAttract(u: SUnit, target: Position): Vector2D {
        val waypoint = map.getPath(u.position, target).firstOrNull()?.center?.toPosition() ?: target
        return (waypoint - u.position).toVector2D().normalize()
    }

    fun intercept(u: SUnit, t: SUnit): Vector2D {
        return t.velocity.multiply(48.0)
                .add(t.position.toVector2D())
                .minus(u.position.toVector2D()).normalize()
    }

    fun airAttract(u: SUnit, target: Position): Vector2D {
        return (target - u.position).toVector2D().normalize()
    }

    fun collisionRepulsion(u: SUnit): Vector2D {
        val collider = units.allunits.nearest(u.x, u.y) { !it.flying && it != u }?.position?.toVector2D()
                ?: return Vector2D()
        return (u.position.toVector2D() - collider).normalize()
    }

    fun keepGroundCompany(u: SUnit, distance: Int): Vector2D {
        val company = units.allunits.nearest(u.x, u.y) { !it.flying && it != u }?.position?.toVector2D()
                ?: return Vector2D()
        val delta = u.position.toVector2D() - company
        return if (delta.length() > distance) delta.normalize().negate() else delta.normalize()
    }

    fun avoidDanger(u: SUnit, safety: Int): Vector2D {
        val enemy = units.enemy.inRadius(u.x, u.y, safety + 384) {
            it.weaponAgainst(u) != WeaponType.None || it.unitType == UnitType.Terran_Bunker
        }?.minBy { it.distanceTo(u) - it.maxRangeVs(u) } ?: return Vector2D()
        return (u.position - enemy.position).toVector2D().normalize()
    }

    fun apply(u: SUnit, force: Vector2D, maxTravel: Double = 128.0) {
        val nForce = force.normalize()
        val ut = u.unitType
        val minFrames = 3
        val minDistance = min(maxTravel, max(32.0, ut.haltDistance() + minFrames * (ut.topSpeed() + 0.5 * minFrames * ut.topSpeed() / max(1, ut.acceleration()))))
        u.moveTo(u.position + nForce.multiply(minDistance).toPosition())
    }
}