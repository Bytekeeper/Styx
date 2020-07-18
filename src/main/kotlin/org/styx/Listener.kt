package org.styx

import bwapi.*
import bwapi.Unit
import org.bk.ass.bt.Best
import org.bk.ass.bt.ExecutionContext
import org.bk.ass.bt.Parallel
import org.styx.Styx.diag
import org.styx.task.*
import java.util.logging.Level

class Listener : DefaultBWListener() {
    private var lastFrame: Int = 0
    private lateinit var game: Game
    private val client = BWClient(this)
    private var maxFrameTime = 0L;

    private val aiTree = Parallel(
            Styx,
            Manners,
            SquadDispatch,
            object : Best(
                    Nine734,
                    NinePoolCheese,
                    TwoHatchHydra,
                    TwoHatchMuta,
                    ThreeHatchMuta,
                    TwelveHatchCheese,
                    TenHatch,
                    FourPool,
                    ThirteenPoolMuta
            ) {
                override fun getUtility(): Double = 0.0
            }.withName("Strategy"),
//            FreeForm,
            WorkerAvoidDamage,
            Gathering(),
            Scouting
    ).withName("AI Tree")

    override fun onStart() {
        game = client.game
        Styx.game = game
        aiTree.init()
        game.sendText("2020-05-13")
        game.setLatCom(false)
    }

    override fun onFrame() {
        lastFrame = game.frameCount
        try {
            val timed = Timed()

            val executionContext = ExecutionContext()
            aiTree.exec(executionContext)
            if (UnitReservation.availableItems.any { it.unitType.isWorker }) {
//                System.err.println("!NO")
            }

//            val w = Styx.units.myWorkers.maxBy {it.id}!!
//            if (Styx.units.myWorkers.size == 12 && !w.moving && !w.gathering && w.buildType == UnitType.None && w.unit.order != Order.ZergBirth) {
//                System.err.println("WCTF")
//            }

//            diag.log("PLAN: " + buildPlan.plannedUnits.joinToString())
            val frameTime = timed.ms()
            if (frameTime > 30 && game.frameCount > 0) {
                diag.log("frame timeout ${game.frameCount} - frame time: ${frameTime}ms", Level.WARNING)
                executionContext.executionTime.toList()
                        .sortedByDescending { (_, time) ->
                            time
                        }.forEach { (node, time) ->
                            diag.log("${node.name} : ${time}ms", Level.WARNING)
                        }
                maxFrameTime = frameTime
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            diag.crash(e)
            throw e
        }
    }

    override fun onUnitDestroy(unit: Unit) {
        Styx.units.onUnitDestroy(unit)
    }

    fun start() {
        try {
            client.startGame()
        } catch (e: Throwable) {
            throw e
        }
    }

    override fun onEnd(isWinner: Boolean) {
        aiTree.close()
        Styx.onEnd(isWinner)
    }
}

fun main(vararg arg: String) {
    Listener().start();
}