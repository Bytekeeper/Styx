package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.unit.GroundAttacker
import org.openbw.bwapi4j.unit.TrainingFacility

object Manners : Action(), TaskProvider {
    override fun processInternal(): TaskStatus {
        if (FTTBot.self.supplyUsed() == 0
                && (FTTBot.self.minerals() < 50 || UnitQuery.my<TrainingFacility>().isEmpty())
                && UnitQuery.enemyUnits.any { it is GroundAttacker }) {
            surrender()
        }
        return TaskStatus.RUNNING
    }

    private fun surrender() {
        FTTBot.game.interactionHandler.sendText("GG")
        FTTBot.game.interactionHandler.leaveGame()
    }

    override fun invoke(): List<Task> = listOf(Manners)
}