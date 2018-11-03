package org.fttbot

import bwem.BWEM
import bwem.map.Map
import org.apache.logging.log4j.LogManager
import org.bk.ass.BWAPI4JAgentFactory
import org.fttbot.info.*
import org.fttbot.strategies.Strategies
import org.fttbot.strategies.Utilities
import org.fttbot.task.*
import org.fttbot.task.Train.Companion.lings
import org.fttbot.task.Train.Companion.mutas
import org.fttbot.task.Train.Companion.ovis
import org.fttbot.task.Train.Companion.workers
import org.locationtech.jts.geom.GeometryFactory
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
import kotlin.math.max
import kotlin.math.min

object FTTBot : BWEventListener {

    private val LOG = LogManager.getLogger()
    val agentFactory = BWAPI4JAgentFactory()
    lateinit var bwemInitializer: Future<BWEM>
    val game = BW(this)
    val geometryFactory = GeometryFactory()
    lateinit var self: Player
    lateinit var enemies: List<Player>

    val bwem: Map get() = bwemInitializer.get().map
    lateinit var render: MapDrawer
    var latency_frames = 0
    var remaining_latency_frames = 0
    var minLatencyFrames = Int.MAX_VALUE
    private var lastUpdateFrame: Int = 0
    var frameCount = 0
    var turnSize: Int = 0

//    private val combatManager = BehaviorTree(AttackEnemyBase())

    private var showDebug: Boolean = false

    private lateinit var bot: MTPar

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
        LOG.info("Version 05/07/18")

        val racePlayed = self.race
        when (racePlayed) {
            Race.Protoss -> FTTConfig.useConfigForProtoss()
            Race.Terran -> FTTConfig.useConfigForTerran()
            Race.Zerg -> FTTConfig.useConfigForZerg()
            else -> throw IllegalStateException("Can't handle race ${racePlayed}")
        }

        UnitQuery.reset()
        EnemyInfo.reset()
        MyInfo.reset()
//        org.fttbot.Map.init()

//        val bo = Strategies.ZERGLING_MADNESS
        val bo = Strategies.TWO_HATCH_MUTA

        bot = MTPar {
            listOf(
                    { bo }, { mutas() + lings() + ovis() + workers() },
                    Manners,
                    /*Train,*/ GatherMinerals, Build, Scouting, WorkerDefense, Expand, CombatController,
                    Upgrade, Research, WorkerTransfer, StrayWorkers
            ).flatMap { it() }
        }
    }

    var tasks = listOf<Task>()


    override fun onFrame() {
        latency_frames = game.interactionHandler.latencyFrames
        remaining_latency_frames = game.interactionHandler.remainingLatencyFrames
        frameCount = game.interactionHandler.frameCount
        minLatencyFrames = min(minLatencyFrames, remaining_latency_frames)
        turnSize = max(turnSize, latency_frames - remaining_latency_frames + 1)

//        val result = bwem.GetMap().GetPath(game.bwMap.startPositions[0].toPosition(), game.bwMap.startPositions[1].toPosition())

        MapQuery.reset()
        Eliza.step()

        if (remaining_latency_frames == minLatencyFrames || lastUpdateFrame + turnSize > frameCount) {
            lastUpdateFrame = frameCount
            UnitQuery.update(game.allUnits)
//        Exporter.export()
            Cluster.step()
            EnemyInfo.step()
            MyInfo.step()

            ProductionBoard.reset()
            ResourcesBoard.reset()
            bot.process()
            tasks = bot.tasks - bot.completedTasks
            if (UnitQuery.myWorkers.any { !it.exists() }) {
                throw IllegalStateException()
            }
        }
        if (showDebug) {
            game.mapDrawer.drawTextScreen(0, 10, "TurnSize: $turnSize, latency: $latency_frames")

            bwem.neutralData.minerals.filter { mineral -> bwem.areas.none { area -> area.minerals.contains(mineral) } }
                    .forEach {
                        game.mapDrawer.drawTextMap(it.center, "ua")
                    }
            UnitQuery.myUnits.forEach {
                val status = MyInfo.unitStatus[it] ?: it.lastCommand
                game.mapDrawer.drawTextMap(it.position, "${it.id}(${status})")
                if (it is MobileUnit && it.targetPosition != null) {
//                    game.mapDrawer.drawLineMap(it.position, it.targetPosition, Color.BLUE)
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
                game.mapDrawer.drawTextMap(it.position, "${it.type}")
            }

//        UnitQuery.myWorkers.filter { it.lastCommand == UnitCommandType.Morph && !it.isMoving}
//                .forEach{
//                    LOG.error("OHOH")
//                }
//            System.exit(1)
            Cluster.squadClusters.clusters.forEach { cluster ->
                val c = cluster.userObject as Cluster<PlayerUnit>
                if (c.enemyUnits.none { it is MobileUnit } && c.myUnits.isEmpty()) return@forEach
                val poly = c.enemyHull.buffer(384.0, 3)
                        .coordinates.map { Position(it.x.toInt(), it.y.toInt()) }
                poly.windowed(2) { p ->
                    game.mapDrawer.drawLineMap(p[0], p[1], Color.RED)
                }
                if (poly.size > 1) {
                    game.mapDrawer.drawLineMap(poly.last(), poly.first(), Color.RED)
                }
                c.myUnits.forEach { u ->
                    game.mapDrawer.drawLineMap(u.position, c.position, Color.GREEN)
                }
                game.mapDrawer.drawCircleMap(c.position, 5, Color.GREEN, true)
                game.mapDrawer.drawTextMap(c.position + Position(10, 10), "%.2f : %.2f".format(
                        c.combatPerformance.first, c.combatPerformance.second
                ))
            }
//            UnitQuery.myUnits.filter { it is MobileUnit }
//                    .forEach {
//                        val areaId = it.position.toWalkPosition().area?.id ?: return@forEach
//                        if (MapQuery.areaPolys[areaId] == null)
//                            return@forEach
//
//                        try {
//
//                            geometryFactory.createPolygon(geometryFactory.createPoint(it.position.toCoordinate())
//                                    .buffer(250.0).coordinates)
//                                    .intersection(MapQuery.areaPolys[areaId])
//                                    .coordinates.map { it.toPosition() }
//                                    .windowed(2) { p ->
//                                        game.mapDrawer.drawLineMap(p[0], p[1], Color.PURPLE)
//                                    }
//                        } catch (e: RuntimeException) {
//                            e.printStackTrace()
//                        }
//
//                    }
//            MapQuery.areaPolys.forEach {
//                it.value.coordinates.map { it.toPosition() }.windowed(2) { p ->
//                    game.mapDrawer.drawLineMap(p[0], p[1], Color.WHITE)
//                }
//            }
//            for (entry in MapQuery.chokePolys) {
//                entry.value.coordinates.map { it.toPosition() }.windowed(2) { p ->
//                    game.mapDrawer.drawLineMap(p[0], p[1], Color.BLUE)
//                }
//            }

            game.mapDrawer.drawTextScreen(0, 40, "Expand : %.2f".format(Utilities.expansionUtility))
            game.mapDrawer.drawTextScreen(0, 50, "Trainers : %.2f".format(Utilities.moreTrainersUtility))
            game.mapDrawer.drawTextScreen(0, 60, "Workers : %.2f".format(Utilities.moreWorkersUtility))
            game.mapDrawer.drawTextScreen(0, 70, "Supply : %.2f".format(Utilities.moreSupplyUtility))
            game.mapDrawer.drawTextScreen(0, 80, "Gas : %.2f".format(Utilities.moreGasUtility))
            game.mapDrawer.drawTextScreen(0, 90, "Lurkers : %.2f".format(Utilities.moreLurkersUtility))
            game.mapDrawer.drawTextScreen(0, 100, "Hydras : %.2f".format(Utilities.moreHydrasUtility))
            game.mapDrawer.drawTextScreen(0, 110, "Mutas : %.2f".format(Utilities.moreMutasUtility))
            game.mapDrawer.drawTextScreen(0, 120, "Ultras : %.2f".format(Utilities.moreUltrasUtility))
            game.mapDrawer.drawTextScreen(0, 130, "Lings : %.2f".format(Utilities.moreLingsUtility))
            game.mapDrawer.drawTextScreen(0, 140, "uWorker : %.2f".format(Utilities.workerUtilization))
            game.mapDrawer.drawTextScreen(0, 150, "uMin : %.2f".format(Utilities.mineralsUtilization))
            game.mapDrawer.drawTextScreen(0, 160, "uGas : %.2f".format(Utilities.gasUtilization))
            game.mapDrawer.drawTextScreen(0, 170, "S : %d".format(self.supplyTotal()))
            game.mapDrawer.drawTextScreen(0, 180, "m/f p/f: %.2f %.2f".format(MyInfo.mineralsPerFrame, MyInfo.gasPerFrame))
            game.mapDrawer.drawTextScreen(0, 190, "board: min: %d, gas %d, supply %d".format(ResourcesBoard.minerals, ResourcesBoard.gas, ResourcesBoard.supply))

//            tasks.forEachIndexed { index, task ->
//                val u = "%.2f".format(task.utility)
//                game.mapDrawer.drawTextScreen(300, index * 10 + 20, "$u : $task")
//            }
        }
    }

    override fun onUnitDestroy(unit: Unit) {
        if (unit !is PlayerUnit) return
        EnemyInfo.onUnitDestroy(unit)
    }

    override fun onUnitCreate(unit: Unit) {
        if (unit !is PlayerUnit) return
    }

    override fun onUnitMorph(unit: Unit) {
    }

    override fun onUnitComplete(unit: Unit) {
        if (unit !is PlayerUnit) return
        LOG.debug("Completed: ${unit}")
    }

    override fun onUnitShow(unit: Unit) {
        if (unit is PlayerUnit && unit.isEnemyUnit) {
            EnemyInfo.onUnitShow(unit)
        }
    }

    override fun onUnitHide(unit: Unit) {
        if (unit is PlayerUnit && unit.isEnemyUnit)
            EnemyInfo.onUnitHide(unit)
        if (unit is PlayerUnit && unit.isMyUnit)
            UnitQuery.andStayDown += unit.id to unit.type
    }

    override fun onUnitRenegade(unit: Unit?) {
        // Unit changed owner
        if (unit is PlayerUnit) {
            EnemyInfo.onUnitRenegade(unit)
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