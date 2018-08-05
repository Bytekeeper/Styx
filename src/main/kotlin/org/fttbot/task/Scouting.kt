package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Overlord
import java.util.*

class Scouting(val unit: MobileUnit) : Task() {
    override val utility: Double = 0.0

    private val move = Move(unit, FTTBot.bwem.bases[rng.nextInt(FTTBot.bwem.bases.size)].center)

    override fun toString(): String = "Scouting with $unit"

    override fun processInternal(): TaskStatus = move.process()

    companion object : TaskProvider {
        private val rng = SplittableRandom()
        val ovis = ManagedTaskProvider({ UnitQuery.myUnits.filterIsInstance<Overlord>() }, { Scouting(it) })
        override fun invoke(): List<Task> = ovis()
    }
}