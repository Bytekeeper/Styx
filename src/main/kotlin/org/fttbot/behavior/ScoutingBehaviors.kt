package org.fttbot.behavior

import bwta.BWTA
import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.*
import org.fttbot.estimation.EnemyModel
import org.fttbot.layer.*
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import java.util.*


class Scout : UnitLT() {
    init {
        guard = Guard()
    }

    override fun execute(): Status {
        val unit = board().unit as MobileUnit
        val order = board().scouting ?: throw IllegalStateException()

        if (order.locations.isEmpty()) return Status.FAILED

        val currentTargetLocation = order.locations.peek()
        if (!unit.isMoving) {
            unit.move(currentTargetLocation)
        }

        if (order.points == null && UnitQuery.unitsInRadius(currentTargetLocation, 300).any { it is Building && it.isEnemyUnit }) {
            EnemyModel.enemyBase = currentTargetLocation
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
            if (unit.position.getDistance(nextPoint) > 80 && (unit.isFlyer || FTTBot.game.bwMap.isWalkable(nextPoint.toWalkable()))) {
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

        return Status.RUNNING
    }

    class Guard : UnitLT() {
        override fun start() {}
        override fun execute(): Status = if (board().scouting != null) Status.SUCCEEDED else Status.FAILED

    }
}


class ScoutEnemyBase : LeafTask<ScoutingBoard>() {
    override fun execute(): Status {
        val time = FTTBot.game.interactionHandler.frameCount
        if (time - `object`.lastScoutFrameCount > 2800) {
            `object`.lastScoutFrameCount = time
            if (UnitQuery.myUnits.any { it.board.scouting != null }) {
                return Status.RUNNING
            }
            val bbUnit = findWorker()?.board ?: return Status.FAILED
            val locations = FTTBot.bwta.getStartLocations().mapTo(ArrayDeque()) { it.tilePosition }
            locations.remove(FTTBot.self.startLocation)
            bbUnit.scouting = Scouting(locations.mapTo(ArrayDeque()) { it.toPosition() })
        }
        return Status.RUNNING
    }

    override fun copyTo(task: Task<ScoutingBoard>?): Task<ScoutingBoard> = ScoutEnemyBase()
}