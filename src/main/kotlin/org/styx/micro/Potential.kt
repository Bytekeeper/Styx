package org.styx.micro

import bwapi.Position
import org.locationtech.jts.math.Vector2D
import org.styx.*
import org.styx.Styx.game
import org.styx.Styx.geography
import org.styx.Styx.map
import org.styx.Styx.units
import kotlin.math.max
import kotlin.math.min

object Potential {

    fun reach(u: SUnit, position: Position) : Vector2D = attract(u, position)

    fun intercept(u: SUnit, t: SUnit): Vector2D {
        val framesDistance = 1.6 * u.distanceTo(t) / (u.topSpeed + 0.1)
        val predictedPosition = t.predictPosition(framesDistance.toInt()).toWalkPosition().makeValid()
        val fixedPredictedPosition =
                if (t.flying)
                    predictedPosition
                else
                    geography.walkRay.tracePath(
                            t.walkPosition,
                            predictedPosition)
        return reach(u, fixedPredictedPosition.middlePosition())
    }


    fun repelFrom(u: SUnit, other: SUnit): Vector2D = repelFrom(u, other.position)

    fun repelFrom(u: SUnit, position: Position): Vector2D =
            (u.position - position).toVector2D().normalize()

    fun attract(u: SUnit, target: Position): Vector2D =
        (target - u.position).toVector2D().normalize()

    fun collisionRepulsion(u: SUnit): Vector2D {
        if (u.flying) return Vector2D()
        val collider = units.allunits.nearest(u.x, u.y, 128) { !it.flying && it != u && it.distanceTo(u) < 32 }?.position?.toVector2D()
                ?: return Vector2D()
        return (u.position.toVector2D() - collider).normalize()
    }

    fun keepGroundCompany(u: SUnit, distance: Int): Vector2D {
        val company = units.allunits.nearest(u.x, u.y, distance) { !it.flying && it != u && it.unitType.canMove() }?.position?.toVector2D()
                ?: return Vector2D()
        val delta = u.position.toVector2D() - company
        return if (delta.length() > distance) delta.normalize().negate() else delta.normalize()
    }

    fun avoidDanger(u: SUnit, safety: Int): Vector2D {
        val enemy = units.enemy.nearest(u.x, u.y, safety + 384) {
            it.inAttackRange(u, safety)
        } ?: return Vector2D()

        return (u.position - enemy.position).toVector2D().normalize()
    }

    fun apply(u: SUnit, force: Vector2D, maxTravel: Double = 128.0) {
        if (u.sleeping)
            return
        val nForce = force.normalize()
        val ut = u.unitType
        val minFrames = 5
        val minDistance = min(maxTravel, max(64.0, ut.haltDistance() + minFrames * (ut.topSpeed() + 0.5 * minFrames * ut.topSpeed() / max(1, ut.acceleration()))))
        val forceAsPosition = nForce.multiply(minDistance).toPosition()
        val dest = u.position + forceAsPosition
        if (u.flying) {
            u.moveTo(dest)
        } else {
            val currentWalkPosition = u.walkPosition
            val requestedTargetWalkPosition = dest.toWalkPosition()
            val requestedTargetLimitedByCollision = geography.walkRay.tracePath(currentWalkPosition, requestedTargetWalkPosition)!!
            if (requestedTargetLimitedByCollision == requestedTargetWalkPosition) {
                u.moveTo(requestedTargetLimitedByCollision.middlePosition())
            } else {
                val dir8Candidates = (0 until 8).map { it * PI2 / 8 }
                        .map {
                            val v = Vector2D(forceAsPosition.length, 0.0)
                                    .rotate(it)
                                    .toPosition().toWalkPosition()
                            val end = (currentWalkPosition + v).makeValid()
                            geography.walkRay.tracePath(currentWalkPosition, end)
                        }
                val endPoints = dir8Candidates.filter {
                    it.getDistance(currentWalkPosition) > 3
                }
                val wpToMoveTo =
                        endPoints.firstOrNull {
                            geography.walkRay.noObstacle(it, requestedTargetWalkPosition)
                        } ?: endPoints.maxBy {
                            currentWalkPosition.getDistance(it)
                        } ?: dir8Candidates.minBy {
                            requestedTargetWalkPosition.getDistance(it)
                        }!!
                u.moveTo(wpToMoveTo.middlePosition())
            }
        }
    }
}