package org.fttbot.behavior

import javafx.concurrent.Task
import org.fttbot.*
import org.fttbot.FTTBot.frameCount
import org.fttbot.info.*
import org.fttbot.search.MCTS
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit
import java.util.logging.Level
import java.util.logging.Logger

object ResearchingOrUpgrading : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit
        if (unit !is ResearchingFacility) return NodeStatus.FAILED
        if (unit.isResearching || unit.isUpgrading) return NodeStatus.RUNNING
        if (board.goal !is Research && board.goal !is Upgrade) return NodeStatus.FAILED
        val goal = board.goal as GoalWithStart
        if (goal.started) {
            board.goal = null
            return NodeStatus.SUCCEEDED
        }
        when (goal) {
            is Research -> {
                if (!unit.canResearch(goal.type)) return NodeStatus.FAILED
                if (!unit.research(goal.type)) {
                    LOG.severe("Couldn't start researching ${goal.type}")
                    return NodeStatus.FAILED
                }
                LOG.info("$unit started researching ${goal.type}")
            }
            is Upgrade -> {
                if (!unit.canUpgrade(goal.type)) return NodeStatus.FAILED
                if (!unit.upgrade(goal.type)) {
                    LOG.severe("Couldn't start upgrading ${goal.type}")
                    return NodeStatus.FAILED
                }
                LOG.info("$unit started upgrading ${goal.type}")
            }
        }
        goal.started = true
        return NodeStatus.RUNNING
    }
}

