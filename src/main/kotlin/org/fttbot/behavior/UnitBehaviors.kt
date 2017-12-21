package org.fttbot.behavior

import com.badlogic.gdx.ai.btree.Task
import org.fttbot.FTTBot
import org.fttbot.LT
import org.fttbot.board


abstract class UnitLT : LT<BBUnit>() {
    override fun start() {
        board().status = this::class.java.simpleName ?: "<Unknown>"
    }
}

const val MOVE_TIMEOUT = 60

class MoveToPosition(var threshold: Double = 5.0) : UnitLT() {
    var bestDistance = Double.MAX_VALUE

    var bestDistanceTime = 0
    var moveOrderWasIssued = false

    override fun execute(): Status {
        val unit = board().unit
        val targetPosition = board().moveTarget ?: return Status.FAILED
        val distance = unit.position.getDistance(targetPosition)
        if (distance <= threshold) {
            return Status.SUCCEEDED
        }
        val elapsedTime = FTTBot.game.elapsedTime()
        if (distance < bestDistance) {
            bestDistanceTime = elapsedTime
            bestDistance = distance
        }
        if (elapsedTime - bestDistanceTime > MOVE_TIMEOUT) {
            return Status.FAILED
        }
        if (!unit.isMoving || !moveOrderWasIssued) {
            moveOrderWasIssued = true
            unit.move(targetPosition)
        }
        return Status.RUNNING
    }

    override fun cpy(): Task<BBUnit> = MoveToPosition(threshold)
}

class AttackTarget : LT<BBUnit>() {
    override fun execute(): Status {
        val unit = board().unit
        val target = board().attacking?.target ?: return Status.FAILED
        if (target.isDead) return Status.SUCCEEDED

        if (!unit.isAttackFrame || !unit.isAttacking || unit.target == target) {
            unit.attack(target)
        }
        return Status.RUNNING
    }

    override fun cpy(): Task<BBUnit> = AttackTarget()
}