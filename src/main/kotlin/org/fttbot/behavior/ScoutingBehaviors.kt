package org.fttbot.behavior

import org.fttbot.*
import org.fttbot.info.EnemyState
import org.fttbot.info.UnitQuery
import org.fttbot.info.board
import org.fttbot.info.canAttack
import org.fttbot.info.isEnemyUnit
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import java.util.*


class Scout : UnitLT() {

    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as MobileUnit
        val order = board.goal as Scouting

        if (order.locations.isEmpty()) return NodeStatus.FAILED

        val currentTargetLocation = order.locations.peek()
        if (!unit.isMoving) {
            unit.move(currentTargetLocation)
        }

        if (order.points == null && UnitQuery.unitsInRadius(currentTargetLocation, 300).any { it is Building && it.isEnemyUnit }) {
            EnemyState.enemyBase = currentTargetLocation
            val regionPoly = FTTBot.bwta.getRegion(currentTargetLocation).polygon
            order.points = regionPoly.points
            val closest = order.points!!.minBy { it.getDistance(unit.position) }
            order.index = order.points!!.indexOf(closest)
        }
        if (order.points != null) {
            val points = order.points!!
            val runFrom = UnitQuery.unitsInRadius(unit.position, 50).firstOrNull() { it is PlayerUnit && it.isEnemyUnit && it.canAttack(unit, 30) }
            if (runFrom != null) {
                // TODO run
            }
            val nextPoint = points[order.index].toVector().scl(0.9f).mulAdd(currentTargetLocation.toVector(), 0.1f).toPosition()
            if (unit.position.getDistance(nextPoint) > 80 && (unit.isFlyer || FTTBot.game.bwMap.isWalkable(nextPoint.toWalkPosition()))) {
                if (unit.targetPosition.getDistance(nextPoint) > 20) {
                    unit.move(nextPoint)
                }
            } else {
                do {
                    order.index = (order.index + 1) % points.size
                } while (points[order.index].getDistance(unit.position) < 120)
            }
        } else if (unit.getDistance(currentTargetLocation) < 200) {
            // Nothing here, move on
            order.locations.pop()
        }

        return NodeStatus.RUNNING
    }
}
