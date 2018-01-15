package org.fttbot.behavior

import org.fttbot.FTTBot
import org.fttbot.Node
import org.fttbot.NodeStatus
import org.fttbot.info.UnitQuery
import org.fttbot.info.board
import org.openbw.bwapi4j.WalkPosition
import org.openbw.bwapi4j.unit.MobileUnit
import java.util.logging.Logger


abstract class UnitLT : Node<BBUnit> {
    protected val LOG = Logger.getLogger(this::class.java.simpleName)

    final override fun tick(board: BBUnit): NodeStatus {
        board.status = this::class.java.simpleName
        return internalTick(board)
    }

    abstract fun internalTick(board: BBUnit): NodeStatus

    override fun aborted(board: BBUnit) {
        board.status = ""
    }
}

class MoveToPosition(var threshold: Double = 12.0) : UnitLT() {
    var bestDistance = Int.MAX_VALUE
    var bestDistanceFrame = 0

    override fun aborted(board: BBUnit) {
        super.aborted(board)
        bestDistance = Int.MAX_VALUE
    }

    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as MobileUnit
        val targetPosition = board.moveTarget ?: return NodeStatus.FAILED
        val distance = unit.position.getDistance(targetPosition)
        if (distance <= threshold) {
            board.moveTarget = null
            unit.stop(false)
            return NodeStatus.SUCCEEDED
        }
        val elapsedFrames = FTTBot.game.interactionHandler.frameCount
        if (distance < bestDistance) {
            bestDistanceFrame = elapsedFrames
            bestDistance = distance
        }
        if (elapsedFrames - bestDistanceFrame > distance * 5 / unit.topSpeed()) {
            return NodeStatus.FAILED
        }
        if (!unit.isMoving || targetPosition.getDistance(unit.targetPosition) >= threshold) {
            unit.move(targetPosition)
        }
        return NodeStatus.RUNNING
    }
}

class MoveTargetFromGoal : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val idle = board.goal as IdleAt
        board.moveTarget = idle.position
        return NodeStatus.SUCCEEDED
    }
}

class SelectSafePosition : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as MobileUnit
        val myBases = UnitQuery.myBases
        if (myBases.isEmpty()) return NodeStatus.FAILED
        val relevantEnemyPositions = UnitQuery.enemyUnits.filter { it.getDistance(unit) < 300 }
        val path = FTTBot.bwem.GetMap().GetPath(unit.position, myBases.first().position)
        // Already in base!
        if (path.isEmpty) return NodeStatus.FAILED
        val target = path.zipWithNext { a, b -> a.Center().add(b.Center()).divide(WalkPosition(2, 2)) }
                .map { it.toPosition() }
                .firstOrNull { spot -> relevantEnemyPositions.map { it.getDistance(spot) }.min() ?: 400.0 > 300 }
        if (target == null) return NodeStatus.FAILED
        unit.board.moveTarget = target
        return NodeStatus.SUCCEEDED
    }
}

