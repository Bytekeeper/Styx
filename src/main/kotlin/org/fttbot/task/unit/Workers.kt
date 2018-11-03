package org.fttbot.task.unit

import org.fttbot.task.Action
import org.fttbot.task.TaskStatus
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType

class UBuild : Action() {
    lateinit var type: UnitType
    lateinit var at: TilePosition

    override fun processInternal(): TaskStatus {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}