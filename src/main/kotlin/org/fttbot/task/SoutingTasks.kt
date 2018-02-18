package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.behavior.Scouting
import org.fttbot.behavior.ScoutingBoard
import org.fttbot.behavior.findWorker
import org.fttbot.info.board
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker
import java.util.*

class ScoutingTask(var scout: PlayerUnit? = null) : Task {
    override val utility: Double
        get() = 1.0

    override fun run(available: Resources): TaskResult {
        val units = available.units
        if (scout != null && (!scout!!.exists() || !units.contains(scout!!))) return TaskResult(status = TaskStatus.FAILED)
        if (scout == null || !units.contains(scout!!)) {
            scout = findWorker(candidates = units.filterIsInstance(Worker::class.java) ) ?: return TaskResult(status = TaskStatus.FAILED)
        }
        if (scout!!.board.goal is Scouting) return TaskResult(Resources(listOf(scout!!)))
        val locations = (FTTBot.game.bwMap.startPositions
                + FTTBot.bwem.GetMap().Areas().flatMap { it.Bases() }.map { it.Location() })
                .sortedBy { FTTBot.game.bwMap.isExplored(it) }
                .toCollection(ArrayDeque())
        locations.remove(FTTBot.self.startLocation)
        scout!!.board.goal = Scouting(locations.mapTo(ArrayDeque()) { it.toPosition() })
        return TaskResult(Resources(listOf(scout!!)))
    }

    companion object {
        fun provideTasksTo(tasks: MutableSet<Task>) {
            val time = FTTBot.game.interactionHandler.frameCount
            if (tasks.none { it is ScoutingTask }) {
                if (time - ScoutingBoard.lastScoutFrameCount > 2800) {
                    ScoutingBoard.lastScoutFrameCount = time
                    tasks.add(ScoutingTask())
                }
            } else {
                ScoutingBoard.lastScoutFrameCount = time
            }
        }
    }
}