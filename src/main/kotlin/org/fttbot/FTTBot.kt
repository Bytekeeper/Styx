package org.fttbot

import bwem.BWEM
import bwem.map.Map
import org.apache.logging.log4j.LogManager
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MSequence.Companion.msequence
import org.fttbot.Parallel.Companion.parallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.estimation.BOPrediction
import org.fttbot.info.*
import org.fttbot.strategies.BuildPlans
import org.fttbot.strategies.Strategies
import org.fttbot.strategies.Utilities
import org.fttbot.task.BoSearch
import org.fttbot.task.Combat.attacking
import org.fttbot.task.Combat.defending
import org.fttbot.task.GatherResources
import org.fttbot.task.Macro.moveSurplusWorkers
import org.fttbot.task.Scouting.scout
import org.fttbot.task.Workers
import org.openbw.bwapi4j.*
import org.openbw.bwapi4j.type.Color
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Unit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.logging.Level
import java.util.logging.Logger

object FTTBot : BWEventListener {

    private val LOG = LogManager.getLogger()
    private lateinit var bwemInitializer: Future<BWEM>
    val game = BW(this)
    lateinit var self: Player
    lateinit var enemies: List<Player>

    val bwem: Map get() = bwemInitializer.get().map
    lateinit var render: MapDrawer
    var latency_frames = 0
    var remaining_latency_frames = 0
    var frameCount = 0

//    private val combatManager = BehaviorTree(AttackEnemyBase())

    private var showDebug: Boolean = false

    private lateinit var bot: Node

    private lateinit var buildQueue: Node


    fun start(showDebug: Boolean) {
        this.showDebug = showDebug
        game.startGame()
    }

    override fun onStart() {
        val start = System.currentTimeMillis()
        println("${System.currentTimeMillis() - start}");
        bwemInitializer = CompletableFuture.supplyAsync {
            val bwem = BWEM(game)
            bwem.initialize()
            bwem
        }
        self = game.interactionHandler.self()
        enemies = listOf(game.interactionHandler.enemy())
        render = game.mapDrawer

        Thread.sleep(100)
        game.interactionHandler.setLocalSpeed(0)
        game.interactionHandler.enableLatCom(false)
//        game.interactionHandler.sendText("black sheep wall")
//        game.interactionHandler.sendText("power overwhelming")

        Logger.getLogger("").level = Level.INFO
        LOG.info("Version 26/06/18")

        val racePlayed = self.race
        when (racePlayed) {
            Race.Protoss -> FTTConfig.useConfigForProtoss()
            Race.Terran -> FTTConfig.useConfigForTerran()
            Race.Zerg -> FTTConfig.useConfigForZerg()
            else -> throw IllegalStateException("Can't handle race ${racePlayed}")
        }

        buildQueue = fallback(
                msequence("buildQueue",
                        BuildPlans.raceChoice(),
                        Inline("Crap Check") {
                            LOG.error("The buildqueue SUCCEEDED, so we're more or less dead: ${buildQueue.tree}")
                            NodeStatus.SUCCEEDED
                        }
                ),
                Inline("Crap Check") {
                    LOG.error("The buildqueue FAILED, so we're more or less dead: ${buildQueue.tree}")
                    LOG.error("Last failed trace: ${buildQueue.getFailTrace()}")
                    NodeStatus.SUCCEEDED
                }

        )
        bot = fallback(
                parallel(100,
                        buildQueue,
                        NoFail(scout()),
                        NoFail(defending()),
                        NoFail(attacking()),
                        NoFail(moveSurplusWorkers()),
                        NoFail(Workers.avoidDamageToWorkers()),
                        NoFail(sequence(GatherResources, Sleep)),
                        NoFail(Workers.returnWanderingWorkers()),
                        NoFail(Strategies.considerCanceling())
                ),
                Inline("This shouldn't have happened") {
                    LOG.error("Main loop failed!")
                    NodeStatus.SUCCEEDED
                }
        )
        UnitQuery.reset()
        Cluster.reset()
        EnemyInfo.reset()
//        org.fttbot.Map.init()
    }

    override fun onFrame() {
        latency_frames = game.interactionHandler.latencyFrames
        remaining_latency_frames = game.interactionHandler.remainingLatencyFrames
        frameCount = game.interactionHandler.frameCount

//        val result = bwem.GetMap().GetPath(game.bwMap.startPositions[0].toPosition(), game.bwMap.startPositions[1].toPosition())

        Eliza.step()

        if (game.interactionHandler.frameCount % latency_frames == 0) {
            UnitQuery.update(game.allUnits)
//        Exporter.export()
            EnemyInfo.step()
            Cluster.step()

            Board.reset()
            bot.tick()
            if (UnitQuery.myWorkers.any { !it.exists() }) {
                throw IllegalStateException()
            }
        }
        if (showDebug) {
            bwem.neutralData.minerals.filter { mineral -> bwem.areas.none { area -> area.minerals.contains(mineral) } }
                    .forEach {
                        game.mapDrawer.drawTextMap(it.center, "ua")
                    }
            UnitQuery.myUnits.forEach {
                game.mapDrawer.drawTextMap(it.position, "${it.id}(${it.lastCommand})")
                if (it is MobileUnit && it.targetPosition != null) {
                    game.mapDrawer.drawLineMap(it.position, it.targetPosition, Color.BLUE)
                }
                if (it is MobileUnit && it.targetUnit != null) {
                    game.mapDrawer.drawLineMap(it.position, it.targetUnit.position, Color.RED)
                }
            }

//        val x = org.fttbot.Map.path(game.bwMap.startPositions[0].toWalkPosition(), game.bwMap.startPositions[1].toWalkPosition())
//
//        x.zipWithNext {
//            a, b -> game.mapDrawer.drawLineMap(a.toPosition(), b.toPosition(), Color.GREEN)
//        }

            EnemyInfo.seenUnits.forEach {
                game.mapDrawer.drawCircleMap(it.position, 16, Color.RED)
                game.mapDrawer.drawTextMap(it.position, "${it.initialType}")
            }

//        UnitQuery.myWorkers.filter { it.lastCommand == UnitCommandType.Morph && !it.isMoving}
//                .forEach{
//                    LOG.error("OHOH")
//                }
            Cluster.myClusters.forEach {
                it.units.forEach { e ->
                    game.mapDrawer.drawLineMap(e.position, it.position, Color.WHITE)
                }
            }
            Cluster.enemyClusters.forEach {
                it.units.forEach { e ->
                    game.mapDrawer.drawLineMap(e.position, it.position, Color.YELLOW)
                }
            }

            game.mapDrawer.drawTextScreen(0, 40, "Expand : %.2f".format(Utilities.expansionUtility))
            game.mapDrawer.drawTextScreen(0, 50, "Trainers : %.2f".format(Utilities.moreTrainersUtility))
            game.mapDrawer.drawTextScreen(0, 60, "Workers : %.2f".format(Utilities.moreWorkersUtility))
            game.mapDrawer.drawTextScreen(0, 70, "Supply : %.2f".format(Utilities.moreSupplyUtility))
            game.mapDrawer.drawTextScreen(0, 80, "Gas : %.2f".format(Utilities.moreGasUtility))
            game.mapDrawer.drawTextScreen(0, 90, "Lurkers : %.2f".format(Utilities.moreLurkersUtility))
            game.mapDrawer.drawTextScreen(0, 100, "Mutas : %.2f".format(Utilities.moreMutasUtility))
        }
    }

    override fun onUnitDestroy(unit: Unit) {
        if (unit !is PlayerUnit) return
        EnemyInfo.onUnitDestroy(unit)
        BoSearch.onUnitDestroy(unit)
    }

    override fun onUnitCreate(unit: Unit) {
        if (unit !is PlayerUnit) return
        BoSearch.onUnitCreate(unit)
    }

    override fun onUnitMorph(unit: Unit) {
    }

    override fun onUnitComplete(unit: Unit) {
        if (unit !is PlayerUnit) return
        LOG.debug("Completed: ${unit}")
    }

    override fun onUnitShow(unit: Unit) {
        if (unit is PlayerUnit && unit.isEnemyUnit) {
            BOPrediction.onSeenUnit(unit)
            EnemyInfo.onUnitShow(unit)
        }
    }

    override fun onUnitHide(unit: Unit) {
        if (unit is PlayerUnit && unit.isEnemyUnit)
            EnemyInfo.onUnitHide(unit)
        if (unit is PlayerUnit && unit.isMyUnit)
            UnitQuery.andStayDown += unit.id to unit.initialType
    }

    override fun onUnitRenegade(unit: Unit?) {
        // Unit changed owner
        if (unit is PlayerUnit) {
            EnemyInfo.onUnitRenegade(unit)
            BoSearch.onUnitRenegade(unit)
        }
    }

    override fun onUnitDiscover(unit: Unit?) {
    }

    override fun onPlayerLeft(player: Player?) {
    }

    override fun onSendText(text: String?) {
    }

    override fun onReceiveText(player: Player, text: String) {
        Eliza.reply(player, text)
    }

    override fun onNukeDetect(target: Position?) {
    }

    override fun onSaveGame(gameName: String?) {
    }

    override fun onUnitEvade(unit: Unit?) {
    }

    override fun onEnd(isWinner: Boolean) {
        game.interactionHandler.sendText("gg")
        if (isWinner) LOG.info("Hurray, I won!")
        else LOG.info("Sad, I lost!")
//        ProcessHelper.killStarcraftProcess()
//        ProcessHelper.killChaosLauncherProcess()
        println()
        println("Exiting...")
//        System.exit(0)
    }
}

fun Double.or(other: Double) = if (isNaN()) other else this