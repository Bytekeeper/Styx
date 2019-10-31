package org.styx

import bwapi.*
import bwapi.Unit
import bwem.BWEM
import org.styx.Styx.bases
import org.styx.Styx.buildPlan
import org.styx.task.FollowBO
import org.styx.task.Gathering
import org.styx.task.Scouting
import org.styx.task.SquadDispatch

class Listener : DefaultBWListener() {
    private lateinit var game: Game
    private val client = BWClient(this)
    private var maxFrameTime = 0L;

    private val aiTree = Par("Main AI Tree",
            SquadDispatch,
            FollowBO,
            Scouting
    )

    override fun onStart() {
        game = client.game
        Styx.game = game
        val bwem = BWEM(game)
        bwem.initialize()
        Styx.map = bwem.map
    }

    override fun onFrame() {
        val timed = Timed()
        Styx.update()

        aiTree.tick()
        ConstructionPosition.drawBlockedAreas()
        game.drawTextScreen(10, 10, "${bases.myBases.size}")
        buildPlan.showPlanned(Position(10, 20))
        val frameTime = timed.ms()
        if (frameTime > maxFrameTime && game.frameCount > 0) {
            System.err.println("frame ${game.frameCount} - max frame time: ${frameTime}ms")
            Styx.frameTimes.forEach {
                System.err.println("${it.name} : ${it.ms()}ms")
            }
            maxFrameTime = frameTime
        }
    }

    override fun onUnitDestroy(unit: Unit) {
        Styx.units.onUnitDestroy(unit)
    }

    fun start() {
        client.startGame()
    }

    override fun onEnd(isWinner: Boolean) {
        Styx.onEnd()
    }
}

fun main(vararg arg: String) {
    Listener().start();
}