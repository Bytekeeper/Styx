package org.fttbot.behavior

import bwapi.WalkPosition
import bwta.BWTA
import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.math.ConvexHull
import org.fttbot.FTTBot
import org.fttbot.board
import org.fttbot.layer.FUnit
import org.fttbot.toPosition
import org.fttbot.toVector
import java.util.*


class Scout : UnitLT() {
    val convexHull = ConvexHull()

    init {
        guard = Guard()
    }

    override fun execute(): Status {
        val unit = board().unit
        val order = board().scouting ?: throw IllegalStateException()

        if (order.locations.isEmpty()) return Status.FAILED

        val currentTargetLocation = order.locations.peek()
        if (!unit.isMoving) {
            unit.move(currentTargetLocation)
        }

        if (order.points == null && FUnit.unitsInRadius(currentTargetLocation, 300).any { it.isBuilding && it.isEnemy }) {
            ScoutingBoard.enemyBase = currentTargetLocation
            val regionPoly = BWTA.getRegion(currentTargetLocation).polygon
            order.points = regionPoly.points
            val closest = order.points!!.minBy { it.getDistance(unit.position) }
            order.index = order.points!!.indexOf(closest)
        }
        if (order.points != null) {
            val points = order.points!!
            val runFrom = FUnit.unitsInRadius(unit.position, 50).firstOrNull() { it.isEnemy && it.canAttack(unit, 30) }
            if (runFrom != null) {
                // TODO run
            }
            val nextPoint = points[order.index].toVector().scl(0.9f).mulAdd(currentTargetLocation.toVector(), 0.1f).toPosition()
            if (unit.position.getDistance(nextPoint) > 80 && (unit.type.isAir || FTTBot.game.isWalkable(WalkPosition(nextPoint.x / 8, nextPoint.y / 8)))) {
                if (unit.targetPosition.getDistance(nextPoint) > 20) {
                    unit.move(nextPoint)
                }
            } else {
                do {
                    order.index = (order.index + 1) % points.size
                } while (points[order.index].getDistance(unit.position) < 120)
            }
        } else if (unit.distanceTo(currentTargetLocation) < 200) {
            // Nothing here, move on
            order.locations.pop()
        }

        return Status.RUNNING
    }

    override fun cpy(): Task<BBUnit> = Scout()

    class Guard : LeafTask<BBUnit>() {
        override fun execute(): Status = if (board().scouting != null) Status.SUCCEEDED else Status.FAILED

        override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Guard()

    }
}


class ScoutEnemyBase : LeafTask<ScoutingBoard>() {
    override fun execute(): Status {
        val time = FTTBot.game.elapsedTime()
        if (time - `object`.lastScoutTime > 80) {
            `object`.lastScoutTime = time
            if (FUnit.myUnits().any { it.board.scouting != null }) {
                return Status.RUNNING
            }
            val bbUnit = findWorker()?.board ?: return Status.FAILED
            val locations = BWTA.getStartLocations().mapTo(ArrayDeque()) { it.tilePosition }
            locations.remove(FTTBot.self.startLocation)
            bbUnit.scouting = Scouting(locations.mapTo(ArrayDeque()) { it.toPosition() })
        }
        return Status.RUNNING
    }

    override fun copyTo(task: Task<ScoutingBoard>?): Task<ScoutingBoard> = ScoutEnemyBase()
}