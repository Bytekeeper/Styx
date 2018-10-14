package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.plus
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.MobileUnit
import java.util.*

class Move(val unit: MobileUnit, var to: Position = unit.position, val tolerance: Int = 32, utility: Double = 1.0) : Action(utility) {
    override fun toString(): String = "Moving $unit to $to"

    private var orderedPosition: Position? = null
    private var failedAttempts = 0
    private val prng = SplittableRandom()

    init {
        assert(unit.exists()) { "Can't order dead $unit to $to" }
        assert(unit.isCompleted) { "Can't order incomplete $unit to $to" }
    }

    override fun reset() {
        super.reset()
        failedAttempts = 0
    }

    override fun processInternal(): TaskStatus {
        if (to.getDistance(unit.position) <= tolerance) {
            if (unit.targetPosition.getDistance(to) > tolerance) {
                unit.move(to)
            }
            return TaskStatus.DONE
        }
        val targetPosition =
                if (unit.isFlying)
                    to
                else
                    FTTBot.bwem.getPath(unit.position, to)
                            .firstOrNull { it.center.toPosition().getDistance(unit.position) >= 160 }?.center?.toPosition()
                            ?: to

        if (unit.targetPosition.getDistance(targetPosition) > 64) {
            if (failedAttempts > 10) {
                return TaskStatus.FAILED
            }
            if (orderedPosition == targetPosition) {
                // Didn't like the order, try a nearby position
                unit.move(targetPosition.plus(Position(prng.nextInt(-32, 32), prng.nextInt(-32, 32))))
                failedAttempts++
            } else {
                unit.move(targetPosition)
            }
            orderedPosition = targetPosition
        }
        return TaskStatus.RUNNING
    }
}
