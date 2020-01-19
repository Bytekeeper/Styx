package org.styx

import bwapi.BWClient
import bwapi.DefaultBWListener
import bwapi.Game
import bwapi.Unit
import org.bk.ass.bt.Best
import org.bk.ass.bt.ExecutionContext
import org.bk.ass.bt.Parallel
import org.styx.Styx.diag
import org.styx.task.*

class Listener : DefaultBWListener() {
    private var lastFrame: Int = 0
    private lateinit var game: Game
    private val client = BWClient(this)
    private var maxFrameTime = 0L;

    private val aiTree = Parallel(Parallel.Policy.SEQUENCE,
            Styx,
            Manners,
            SquadDispatch,
            ReactByUpgradingOverlordSpeed,
            ReactWithLings,
            ReactByCancellingDyingBuildings,
            ReactWithMutasForTerranFlyingBuildings,
            Best(
                    Nine734,
                    NinePoolCheese,
                    TwoHatchHydra,
                    TwoHatchMuta,
                    ThreeHatchMuta,
                    TenHatch,
                    FourPool

                    // Actually crappy strategies:
//                    ThirteenPoolMuta,
            ).withName("Strategy"),
            WorkerAvoidDamage,
            Gathering(),
            Scouting
    ).withName("AI Tree")

    override fun onStart() {
        game = client.game
        Styx.game = game
        aiTree.init()
    }

    override fun onFrame() {
        lastFrame = game.frameCount
        try {
            val timed = Timed()

            val executionContext = ExecutionContext()
            aiTree.exec(executionContext)
//            diag.log("PLAN: " + buildPlan.plannedUnits.joinToString())
            val frameTime = timed.ms()
            if (frameTime > 42 && game.frameCount > 0) {
                diag.log("frame ${game.frameCount} - frame time: ${frameTime}ms")
                executionContext.executionTime.toList()
                        .sortedByDescending { (_, time) ->
                            time
                        }.forEach { (node, time) ->
                            diag.log("${node.name} : ${time}ms")
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
        aiTree.close()
        Styx.onEnd(isWinner)
    }
}

fun main(vararg arg: String) {
    Listener().start();
}