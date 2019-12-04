package org.styx.micro

import bwapi.Position
import bwapi.UnitType
import bwapi.WeaponType
import bwem.ChokePoint
import org.locationtech.jts.math.Vector2D
import org.styx.*
import org.styx.Styx.game
import org.styx.Styx.geography
import org.styx.Styx.map
import org.styx.Styx.units
import kotlin.math.max
import kotlin.math.min

object Potential {
    fun groundAttract(u: SUnit, target: Position): Vector2D {
        val unitWP = u.walkPosition
        val targetWP = target.toWalkPosition()

        if (!u.flying) {
//            val image = geography.walkable.toBufferedImage()
//            val g = image.createGraphics()
//            g.color = Color.RED
//            g.drawLine(unitWP.x, unitWP.y, targetWP.x, targetWP.y)
//            g.color = Color.WHITE
//            g.fillArc(unitWP.x - 2, unitWP.y - 2, 4, 4, 0, 360)
//            val area = map.getArea(unitWP)
//            if (area != null) {
//                val ha = area.walkPositionWithHighestAltitude
//                g.color = Color.BLUE
//                g.fillArc(ha.x - 2, ha.y - 2, 4, 4, 0, 360)
//                map.getPath(u.position, target).firstOrNull()
//                        ?.let {
//                            val pos = it.center
//                            g.color = Color.CYAN
//                            g.drawLine(unitWP.x, unitWP.y, pos.x, pos.y)
//                        }
//            }
//            for (c in map.chokePoints) {
//                g.color = Color.PINK
//                g.fillArc(c.center.x - 2, c.center.y - 2, 4, 4, 0, 360)
//                g.color = Color.ORANGE
//                val a = c.getNodePosition(ChokePoint.Node.END1)
//                g.fillArc(a.x - 2, a.y - 2, 4, 4, 0, 360)
//                val b = c.getNodePosition(ChokePoint.Node.END2)
//                g.fillArc(b.x - 2, b.y - 2, 4, 4, 0, 360)
//            }
//            diag.dump("groundAttract", image)
        }
        val waypoint =
                if (geography.walkRay.noObstacle(unitWP, targetWP)) target
                else {
                    val myArea = map.getArea(unitWP)
                    val path = map.getPath(u.position, target)
                    if (!path.isEmpty) {
                        val cp = path.first()
                        val nextArea =
                                if (path.size() == 1)
                                    if (cp.areas.left == myArea) cp.areas.right else cp.areas.left
                                else if (path[1].areas.left == cp.areas.left || path[1].areas.left == cp.areas.right) {
                                    path[1].areas.left
                                } else
                                    path[1].areas.right
                        val pos = sequenceOf(ChokePoint.Node.END1, ChokePoint.Node.END2, ChokePoint.Node.MIDDLE)
                                .map { node ->
                                    cp.getNodePositionInArea(node, nextArea)
                                }.minBy { it.getDistance(unitWP) + it.getDistance(targetWP) }!!

                        pos.toPosition()
                    } else {
                        target
                    }
                }
        return (waypoint - u.position).toVector2D().normalize()
    }

    fun reach(u: SUnit, position: Position) : Vector2D {
        return if (u.flying)
            airAttract(u, position)
        else if (game.isWalkable(u.walkPosition))
            groundAttract(u, position)
        else
            Vector2D()
    }

    fun intercept(u: SUnit, t: SUnit): Vector2D {
        val framesDistance = 2.0 * u.distanceTo(t) / (u.topSpeed + 0.1)
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


    fun airAttract(u: SUnit, target: Position): Vector2D {
        return (target - u.position).toVector2D().normalize()
    }

    fun collisionRepulsion(u: SUnit): Vector2D {
        val collider = units.allunits.nearest(u.x, u.y) { !it.flying && it != u && it.distanceTo(u) < 32 }?.position?.toVector2D()
                ?: return Vector2D()
        return (u.position.toVector2D() - collider).normalize()
    }

    fun keepGroundCompany(u: SUnit, distance: Int): Vector2D {
        val company = units.allunits.nearest(u.x, u.y) { !it.flying && it != u && it.unitType.canMove() }?.position?.toVector2D()
                ?: return Vector2D()
        val delta = u.position.toVector2D() - company
        return if (delta.length() > distance) delta.normalize().negate() else delta.normalize()
    }

    fun avoidDanger(u: SUnit, safety: Int): Vector2D {
        val enemy = units.enemy.nearest(u.x, u.y) {
            (it.hasWeaponAgainst(u) || it.unitType == UnitType.Terran_Bunker) &&
                    it.distanceTo(u) - it.maxRangeVs(u) < safety
        } ?: return Vector2D()

        return (u.position - enemy.position).toVector2D().normalize()
    }

    fun apply(u: SUnit, force: Vector2D, maxTravel: Double = 128.0) {
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
                    it.getDistance(currentWalkPosition) > 5
                }
                val wpToMoveTo =
                        endPoints.firstOrNull {
                            geography.walkRay.noObstacle(it, requestedTargetWalkPosition)
                        } ?: endPoints.minBy {
                            requestedTargetWalkPosition.getDistance(it)
                        } ?: dir8Candidates.minBy {
                            requestedTargetWalkPosition.getDistance(it)
                        }!!
                u.moveTo(wpToMoveTo.middlePosition())
            }
        }
    }
}