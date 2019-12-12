package org.styx

import bwapi.BWClient
import bwapi.DefaultBWListener
import bwapi.Game
import bwapi.Unit
import bwem.BWEM
import org.styx.Styx.diag
import org.styx.task.*

class Listener : DefaultBWListener() {
    private var lastFrame: Int = 0
    private lateinit var game: Game
    private val client = BWClient(this)
    private var maxFrameTime = 0L;

    private val aiTree = Par("Main AI Tree",
            true,
            squadDispatch,
            Best("Strategy",
                    Nine734,
                    NinePoolCheese,
                    TwoHatchHydra,
                    TwoHatchMuta
            ),
            Gathering(),
            Scouting
    )

    override fun onStart() {
        game = client.game
        Styx.game = game
        val bwem = BWEM(game)
        bwem.initialize()
        Styx.map = bwem.map
        Styx.init()
        aiTree.loadLearned()
    }

    override fun onFrame() {
        lastFrame = game.frameCount
        try {
            val timed = Timed()
            Styx.update()


            aiTree()
//            diag.log("PLAN: " + buildPlan.plannedUnits.joinToString())
            val frameTime = timed.ms()
            if (frameTime > 42 && game.frameCount > 0) {
                diag.log("frame ${game.frameCount} - frame time: ${frameTime}ms")
                Styx.frameTimes
                        .filter { it.ms() > 0 }
                        .forEach {
                            diag.log("${it.name} : ${it.ms()}ms")
                }
                maxFrameTime = frameTime
            }
        } catch (e: Throwable) {
            diag.crash(e)
            throw e
        }
    }

    override fun onUnitDestroy(unit: Unit) {
        Styx.units.onUnitDestroy(unit)
    }

    fun start() {
        client.startGame()
    }

    override fun onEnd(isWinner: Boolean) {
        Styx.onEnd(isWinner)
        aiTree.saveLearned()
    }
}

fun main(vararg arg: String) {
    Listener().start();
}