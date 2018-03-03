package org.fttbot

import bwem.BWEM
import bwta.BWTA
import org.fttbot.CompoundActions.build
import org.fttbot.CompoundActions.train
import org.fttbot.estimation.BOPrediction
import org.fttbot.info.*
import org.fttbot.task.BoSearch
import org.fttbot.task.ConstructBuilding
import org.fttbot.task.GatherResources
import org.openbw.bwapi4j.*
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.UnitType
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

    val bot = BTree(
            All(Fallback(ToNodes({ ProductionQueue.queue }, {
                        ProductionQueue.setInProgress(it)
                        when (it) {
                            is BOUnit -> if (it.type.isAddon) throw UnsupportedOperationException()
                            else if (it.type.isBuilding)
                                build(it.type, it.position)
                            else
                                train(it.type)
                            else -> throw UnsupportedOperationException()
                        }
                    })),
                    GatherResources)
    )

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

        ProductionQueue.enqueue(listOf(
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Hatchery),
                BOUnit(UnitType.Zerg_Spawning_Pool),
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Overlord),
                BOUnit(UnitType.Zerg_Extractor),
                BOUnit(UnitType.Zerg_Zergling),
                BOUnit(UnitType.Zerg_Zergling),
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Drone),
                BOUnit(UnitType.Zerg_Zergling),
                BOUnit(UnitType.Zerg_Zergling)
        ))
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
            ProductionQueue.onFrame()
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
        checkForStartedConstruction(unit)
        BoSearch.onUnitCreate(unit)
    }

    private fun checkForStartedConstruction(unit: PlayerUnit) {
        if (unit.isMyUnit) {
            ProductionQueue.onStarted(unit)
        }
    }

    override fun onUnitMorph(unit: Unit) {
        if (unit !is PlayerUnit) return
        checkForStartedConstruction(unit)
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