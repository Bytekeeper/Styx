package org.fttbot

import bwem.BWEM
import bwta.BWTA
import org.fttbot.CompoundActions.trainWorker
import org.fttbot.estimation.BOPrediction
import org.fttbot.info.*
import org.fttbot.task.BoSearch
import org.fttbot.task.GatherResources
import org.openbw.bwapi4j.*
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Unit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.logging.Level
import java.util.logging.Logger

object FTTBot : BWEventListener {

    private val LOG = Logger.getLogger(this::class.java.simpleName)
    private lateinit var bwtaInitializer: Future<BWTA>
    private lateinit var bwemInitializer: Future<BWEM>
    val game = BW(this)
    lateinit var self: Player
    lateinit var enemy: Player

    val bwta: BWTA by lazy(LazyThreadSafetyMode.NONE) {
        bwtaInitializer.get()
    }
    val bwem: BWEM by lazy(LazyThreadSafetyMode.NONE) {
        bwemInitializer.get()
    }
    lateinit var render: MapDrawer
    var latency_frames = 0
    var remaining_latency_frames = 0
    var frameCount = 0

//    private val combatManager = BehaviorTree(AttackEnemyBase())

    fun start() {
        game.startGame()
    }

    val bwtaAvailable get() = bwtaInitializer.isDone

    lateinit var bot : Node

    private lateinit var buildQueue: Node

    override fun onStart() {
        bwtaInitializer = CompletableFuture.supplyAsync {
            val bwta = BWTA()
            bwta.analyze()
            bwta
        }
        bwemInitializer = CompletableFuture.supplyAsync {
            val bwem = BWEM(game)
            bwem.initialize()
            bwem
        }
        self = game.interactionHandler.self()
        enemy = game.interactionHandler.enemy()
        render = game.mapDrawer

        Thread.sleep(100)
        game.interactionHandler.setLocalSpeed(0)
        game.interactionHandler.enableLatCom(false)

        Logger.getLogger("").level = Level.INFO

        val racePlayed = self.race
        when (racePlayed) {
            Race.Protoss -> FTTConfig.useConfigForProtoss()
            Race.Terran -> FTTConfig.useConfigForTerran()
            Race.Zerg -> FTTConfig.useConfigForZerg()
            else -> throw IllegalStateException("Can't handle race ${racePlayed}")
        }

        buildQueue = MAll(
                trainWorker(),
                trainWorker(),
                trainWorker(),
                trainWorker(),
                trainWorker(),
                trainWorker(),
                trainWorker(),
                trainWorker()
//                build(UnitType.Zerg_Hatchery),
//                build(UnitType.Zerg_Spawning_Pool),
//                trainWorker(),
//                trainWorker(),
//                trainWorker(),
//                buildOrTrainSupply(),
//                build(UnitType.Zerg_Extractor),
//                train(UnitType.Zerg_Zergling),
//                train(UnitType.Zerg_Zergling),
//                trainWorker(),
//                trainWorker(),
//                trainWorker(),
//                trainWorker(),
//                train(UnitType.Zerg_Zergling),
//                train(UnitType.Zerg_Zergling)
        )
        bot = MAll(buildQueue, Sequence(GatherResources, Sleep))
        UnitQuery.update(emptyList())
    }

    override fun onFrame() {
        latency_frames = game.interactionHandler.latencyFrames
        remaining_latency_frames = game.interactionHandler.remainingLatencyFrames
        frameCount = game.interactionHandler.frameCount

//        val result = bwem.GetMap().GetPath(game.bwMap.startPositions[0].toPosition(), game.bwMap.startPositions[1].toPosition())

        if (game.interactionHandler.frameCount % latency_frames == 0) {
            UnitQuery.update(game.allUnits)
//        Exporter.export()
            EnemyState.step()
            Cluster.step()
            ClusterUnitInfo.step()

            Board.resources.reset(self.minerals(), self.gas(), self.supplyTotal() - self.supplyUsed(), UnitQuery.myUnits)
            bot.tick()
        }

    }

    override fun onUnitDestroy(unit: Unit) {
        if (unit !is PlayerUnit) return
        EnemyState.onUnitDestroy(unit)
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
        LOG.info("Completed: ${unit}")
    }

    override fun onUnitShow(unit: Unit) {
        if (unit is PlayerUnit && unit.isEnemyUnit) {
            BOPrediction.onSeenUnit(unit)
            EnemyState.onUnitShow(unit)
        }
    }

    override fun onUnitHide(unit: Unit) {
        if (unit is PlayerUnit && unit.isEnemyUnit) EnemyState.onUnitHide(unit)
    }

    override fun onUnitRenegade(unit: Unit?) {
        // Unit changed owner
        if (unit is PlayerUnit) {
            EnemyState.onUnitRenegade(unit)
            BoSearch.onUnitRenegade(unit)
        }
    }

    override fun onUnitDiscover(unit: Unit?) {
    }

    override fun onPlayerLeft(player: Player?) {
    }

    override fun onSendText(text: String?) {
    }

    override fun onReceiveText(player: Player?, text: String?) {
    }

    override fun onNukeDetect(target: Position?) {
    }

    override fun onSaveGame(gameName: String?) {
    }

    override fun onUnitEvade(unit: Unit?) {
    }

    override fun onEnd(isWinner: Boolean) {
        if (isWinner) LOG.info("Hurray, I won!")
        else LOG.info("Sad, I lost!")
        ProcessHelper.killStarcraftProcess()
        ProcessHelper.killChaosLauncherProcess()
        println()
        println("Exiting...")
        System.exit(0)
    }
}

fun Double.or(other: Double) = if (isNaN()) other else this