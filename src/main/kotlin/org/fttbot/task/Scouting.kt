package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.Locked
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Overlord
import java.util.*

class Scouting(val unit: MobileUnit) : Task() {
    override val utility: Double = 0.0

    private val move by SubTask { Move(unit, utility = 0.5) }

    private val avoid by SubTask { AvoidDamage(unit) }
    private val targetPosition = Locked<Position>(this) { UnitQuery.inRadius(it, 100).isEmpty() }

    override fun toString(): String = "Scouting with $unit"

    override fun processInternal(): TaskStatus {
        move.to = targetPosition.compute {
            val position = FTTBot.bwem.bases[rng.nextInt(FTTBot.bwem.bases.size)].center
            if (it(position)) position else null
        } ?: return TaskStatus.RUNNING
        return processInSequence(move, avoid)
    }

    companion object : TaskProvider {
        private val rng = SplittableRandom()
        val ovis = ManagedTaskProvider({ UnitQuery.myUnits.filterIsInstance<Overlord>() }, { Scouting(it) })
        override fun invoke(): List<Task> = ovis()
    }
}