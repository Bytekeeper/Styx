package org.styx

import bwapi.BWClient
import bwapi.DefaultBWListener
import bwapi.Game
import bwapi.Unit
import bwem.BWEM
import org.styx.Styx.bases
import org.styx.task.SquadDispatch
import org.styx.task.FollowBO
import org.styx.task.Gathering

class Listener : DefaultBWListener() {
    private lateinit var game: Game
    private val client = BWClient(this)

    private val aiTree = BehaviorTree(Par("Main AI Tree",
            SquadDispatch,
            FollowBO,
            Gathering
    ))

    override fun onStart() {
        game = client.game
        Styx.game = game
        val bwem = BWEM(game)
        bwem.initialize()
        Styx.map = bwem.map
    }

    override fun onFrame() {
        Styx.update()

        aiTree.tick()
        ConstructionPosition.drawBlockedAreas()
        game.drawTextScreen(10, 10, "${bases.myBases.size}")
    }

    override fun onUnitDestroy(unit: Unit) {
        Styx.units.onUnitDestroy(unit)
    }

    fun start() {
        client.startGame()
    }
}

fun main(vararg arg: String) {
    Listener().start();
}