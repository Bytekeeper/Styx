package org.styx.actions

import bwapi.Unit
import org.styx.Context.actionBoards

class ActionBoard {
}

class ActionBoards {
    private val boards = mutableMapOf<Unit, ActionBoard>()

    fun reset() {
        boards.clear()
    }

    fun update() {

    }

    companion object {
        fun Unit.actionBoard() = actionBoards.boards.computeIfAbsent(this) { ActionBoard() }
    }
}