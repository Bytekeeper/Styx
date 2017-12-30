package org.fttbot.behavior

import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.FTTBot
import org.fttbot.board
import org.openbw.bwapi4j.unit.MobileUnit
import java.util.logging.Logger


abstract class UnitLT : LeafTask<BBUnit>() {
    protected val LOG = Logger.getLogger(this::class.java.simpleName)

    override fun start() {
        board().status = this::class.java.simpleName ?: "<Unknown>"
        LOG.fine("${board().unit} started ${board().status}")
    }

    override fun end() {
        LOG.finest("${board().unit} stopped ${board().status} with ${status}")
    }

    override fun copyTo(task: Task<BBUnit>): Task<BBUnit> = task
}

const val MOVE_TIMEOUT = 60

class MoveToPosition(var threshold: Double = 12.0) : UnitLT() {
    var bestDistance = Int.MAX_VALUE
    var bestDistanceFrame = 0

    override fun start() {
        super.start()
        bestDistance = Int.MAX_VALUE
    }

    override fun execute(): Status {
        val unit = board().unit as MobileUnit
        val targetPosition = board().moveTarget ?: return Status.FAILED
        val distance = unit.position.getDistance(targetPosition)
        if (distance <= threshold) {
            return Status.SUCCEEDED
        }
        val elapsedFrames = FTTBot.game.interactionHandler.frameCount
        if (distance < bestDistance) {
            bestDistanceFrame = elapsedFrames
            bestDistance = distance
        }
        if (elapsedFrames - bestDistanceFrame > distance * 5 / unit.topSpeed()) {
            return Status.FAILED
        }
        if (!unit.isMoving || targetPosition.getDistance(unit.targetPosition) >= threshold) {
            unit.move(targetPosition)
        }
        return Status.RUNNING
    }

    override fun copyTo(task: Task<BBUnit>): Task<BBUnit> {
        (task as MoveToPosition).threshold = threshold
        return task
    }
}

