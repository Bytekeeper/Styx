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

    override fun run(units: List<PlayerUnit>): TaskResult {
        if (scout != null && (!scout!!.exists() || !units.contains(scout!!))) return TaskResult(emptyList(), TaskStatus.FAILED)
        if (scout == null || !units.contains(scout!!)) {
            scout = findWorker(candidates = units.filterIsInstance(Worker::class.java) ) ?: return TaskResult(emptyList(), TaskStatus.FAILED)
        }
        if (scout!!.board.goal is Scouting) return TaskResult(listOf(scout!!))
        val locations = FTTBot.bwta
                .getStartLocations()
                .sortedBy { FTTBot.game.bwMap.isExplored(it.tilePosition) }
                .mapTo(ArrayDeque()) { it.tilePosition }
        locations.remove(FTTBot.self.startLocation)
        scout!!.board.goal = Scouting(locations.mapTo(ArrayDeque()) { it.toPosition() })
        return TaskResult(listOf(scout!!))
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