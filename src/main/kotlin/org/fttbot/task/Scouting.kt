package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.Locked
import org.fttbot.ResourcesBoard
import org.fttbot.info.UnitQuery
import org.fttbot.task.move.AvoidCombat
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Overlord
import java.util.*

class Scouting(val unit: MobileUnit) : Task() {
    override val utility: Double = 0.0

    private val move by LazyTask { Move(unit, utility = 0.5) }

    private val avoid by LazyTask { AvoidCombat(unit) }
    private val targetPosition = Locked<Position>(this) { UnitQuery.inRadius(it, 100).isEmpty() }

    override fun toString(): String = "Scouting with $unit"

    override fun processInternal(): TaskStatus {
        move.to = targetPosition.compute {
            val unexploredLocation = FTTBot.game.bwMap.startPositions.filter { !FTTBot.game.bwMap.isExplored(it) }
            val position =
                    if (unexploredLocation.isNotEmpty()) unexploredLocation[rng.nextInt(unexploredLocation.size)].toPosition()
                    else FTTBot.bwem.bases[rng.nextInt(FTTBot.bwem.bases.size)].center
            if (it(position)) position else null
        } ?: return TaskStatus.RUNNING
        val result = processInSequence(move, avoid)
        return result
    }

    companion object : TaskProvider {
        private val rng = SplittableRandom()
        val ovis = ItemToTaskMapper({ ResourcesBoard.completedUnits.filterIsInstance<Overlord>() }, { Scouting(it).nvr() })
        override fun invoke(): List<Task> = ovis()
    }
}