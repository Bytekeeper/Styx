package org.styx.micro

import bwapi.Position
import bwapi.UnitType
import org.locationtech.jts.math.Vector2D
import org.styx.*
import org.styx.Styx.units
import kotlin.math.max

class Force(private val forces: MutableList<Vector2D> = mutableListOf()) {
    val minFrames = 4

    fun reach(u: SUnit, position: Position, factor: Double = 1.0) = attract(u, position, factor)

    fun pursuit(u: SUnit, t: SUnit, factor: Double = 1.0): Force {
        val framesDistance = 1.6 * u.distanceTo(t) / (u.topSpeed + 0.1)
        val predictedPosition = t.predictPosition(framesDistance.toInt()).toWalkPosition().makeValid()
        val fixedPredictedPosition =
                if (t.flying)
                    predictedPosition
                else
                    t.tracePath(predictedPosition)
        reach(u, fixedPredictedPosition.middlePosition(), factor)
        return this
    }

    private fun addForce(force: Vector2D): Force {
        forces += force
        return this
    }

    val hasForces get() = forces.isNotEmpty()

    fun repelFrom(u: SUnit, other: SUnit, factor: Double = 1.0) = repelFrom(u, other.position, factor)

    fun repelFrom(u: SUnit, position: Position, factor: Double = 1.0) =
            addForce((u.position - position).toVector2D().normalize() * factor)

    fun attract(u: SUnit, target: Position, factor: Double = 1.0) =
            addForce((target - u.position).toVector2D().normalize() * factor)

    fun collisionRepulsion(u: SUnit, factor: Double = 1.0, includeMovables: Boolean = true): Force {
        if (u.flying) return this
        val collider = units.allunits.nearest(u.x, u.y, 128) { !it.flying && (includeMovables || !it.unitType.canMove()) && it != u && it.distanceTo(u) < 32 }?.position?.toVector2D()
                ?: return this
        return addForce((u.position.toVector2D() - collider).normalize() * factor)
    }

    fun keepGroundCompany(u: SUnit, distance: Int, factor: Double): Force {
        val company = units.allunits.nearest(u.x, u.y, distance) { !it.flying && it != u && it.unitType.canMove() }?.position?.toVector2D()
                ?: return this
        val delta = u.position.toVector2D() - company
        return addForce((if (delta.length() > distance) delta.normalize().negate() else delta.normalize()) * factor)
    }

    fun avoidDanger(u: SUnit, safety: Int, factor: Double = 1.0): Force {
        val enemy = units.enemy.nearest(u.x, u.y, safety + 384) {
            it.inAttackRange(u, safety)
        } ?: return this

        return addForce((u.position - enemy.position).toVector2D().normalize() * factor)
    }

    fun embraceDanger(u: SUnit, safety: Int, factor: Double = 1.0) = avoidDanger(u, safety, -factor)

    fun apply(u: SUnit, maxTravel: Double = 128.0) {
        if (u.sleeping)
            return
        if (u.unitType == UnitType.Zerg_Zergling && u.threats.any { u.inAttackRange(it, 8) }) {
//            println("w00t")
        }

        var forceSum = Vector2D()
        val ut = u.unitType
        val speedFactor = max(16.0, ut.haltDistance() / 256.0 + minFrames * (ut.topSpeed() + 0.5 * minFrames * ut.topSpeed() / max(1, ut.acceleration())))
        for (f in forces) {
            if (forceSum.lengthSquared() >= 1.0) break;
            forceSum = forceSum.add(f)
        }
        val forceAsPosition = forceSum.multiply(speedFactor).toPosition()
        val dest = u.position + forceAsPosition
        if (u.flying) {
            u.moveTo(dest)
        } else {
            val requestedTargetWalkPosition = dest.toWalkPosition().makeValid()
            val requestedTargetLimitedByCollision = u.tracePath(requestedTargetWalkPosition)
            if (requestedTargetLimitedByCollision == requestedTargetWalkPosition) {
                u.moveTo(requestedTargetLimitedByCollision.middlePosition())
            } else {
                val dir8Candidates = (0 until 8).map { it * PI2 / 8 }
                        .map {
                            val v = Vector2D(forceAsPosition.length, 0.0)
                                    .rotate(it)
                                    .toPosition().toWalkPosition()
                            val end = (u.walkPosition + v).makeValid()
                            u.tracePath(end)
                        }
                val wpToMoveTo = dir8Candidates.maxWith(compareBy({ it.getDistance(u.walkPosition) }, { -it.getDistance(requestedTargetWalkPosition) }))!!
                u.moveTo(wpToMoveTo.middlePosition())
            }
        }
    }
}