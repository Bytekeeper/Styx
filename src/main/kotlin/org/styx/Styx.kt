package org.styx

import bwapi.BWClient
import bwapi.DefaultBWListener
import bwapi.Unit
import bwem.BWEM
import org.styx.Context.economy
import org.styx.Context.frameCount
import org.styx.Context.game
import org.styx.Context.map
import org.styx.Context.recon
import org.styx.Context.reset
import org.styx.Context.self
import org.styx.Context.taskProviders
import org.styx.Context.turnSize
import org.styx.Context.update
import org.styx.Context.visualization
import kotlin.math.max
import kotlin.math.min

class Styx(private val debug: Boolean = false) : DefaultBWListener() {
    private val bwClient = BWClient(this)
    private var latency_frames: Int = 0
    private var lastUpdateFrame: Int = 0

    private var minLatencyFrames: Int = Int.MAX_VALUE


    fun start() {
        bwClient.startGame()
    }


    override fun onStart() {
        game = bwClient.game
        game.setLatCom(false)
        game.setLocalSpeed(0)

        self = game.self()
        val bwem = BWEM(game)
        bwem.initialize()
        map = bwem.map

        reset()
    }

    override fun onFrame() {
        frameCount = game.frameCount
        turnSize = max(turnSize, latency_frames - game.remainingLatencyFrames + 1)
        latency_frames = game.latencyFrames
        minLatencyFrames = min(minLatencyFrames, game.remainingLatencyFrames)

        if (game.remainingLatencyFrames == minLatencyFrames || lastUpdateFrame + turnSize > frameCount) {
            lastUpdateFrame = frameCount

            update()

//            if (find.myUnits.any { it.type == UnitType.Zerg_Zergling && it.isCompleted }) {
//                println("Zergling at $frameCount")
//                exitProcess(0)
//            }

            taskProviders.taskProviders
                    .flatMap { it() }
                    .sortedByDescending { it.priority }
                    .forEach { it.execute() }
        }

        var y = 20
        if (visualization.drawGatheringStats) {
            game.drawTextScreen(10, y, "Minerals per drone/frame: %.4f".format(economy.mineralsPerFramePerWorker))
            y += 10
            game.drawTextScreen(10, y, "Gas per drone/frame: %.4f".format(economy.gasPerFramePerWorker))
            y += 10
        }
    }

    override fun onUnitHide(unit: Unit) {
        recon.onUnitHide(unit)
    }

    override fun onUnitDestroy(unit: Unit) {
        recon.onUnitDestroy(unit)
    }
}
