package org.fttbot.task

import org.openbw.bwapi4j.unit.MobileUnit

class AvoidCombat(private val unit: MobileUnit, utility: Double) : Action(utility) {
    override fun processInternal(): TaskStatus {
        return TaskStatus.DONE
    }

}