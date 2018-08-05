package org.fttbot.task

import org.openbw.bwapi4j.unit.PlayerUnit

class CoordinatedAttack(var attackers: List<PlayerUnit> = listOf(), var targets: List<PlayerUnit> = listOf()) : Task() {
    override val utility: Double
        get() = 1.0

    override fun processInternal(): TaskStatus {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}