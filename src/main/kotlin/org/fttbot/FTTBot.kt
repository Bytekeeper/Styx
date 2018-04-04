package org.fttbot

import bwem.BWEM
import bwem.map.Map
import bwta.BWTA
import org.apache.logging.log4j.LogManager
import org.fttbot.FTTBot.enemy
import org.fttbot.FTTBot.frameCount
import org.fttbot.FTTBot.game
import org.fttbot.FTTBot.latency_frames
import org.fttbot.FTTBot.remaining_latency_frames
import org.fttbot.FTTBot.self
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MSequence.Companion.msequence
import org.fttbot.Parallel.Companion.parallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.estimation.BOPrediction
import org.fttbot.info.*
import org.fttbot.strategies.ZvP
import org.fttbot.task.BoSearch
import org.fttbot.task.Combat.attacking
import org.fttbot.task.Combat.defending
import org.fttbot.task.GatherResources
import org.fttbot.task.Macro.moveSurplusWorkers
import org.fttbot.task.Macro.preventSupplyBlock
import org.fttbot.task.Scouting.scout
import org.openbw.bwapi4j.*
import org.openbw.bwapi4j.type.Color
import org.openbw.bwapi4j.type.Race
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
    lateinit var enemy: Player

    val bwem: Map get() = bwemInitializer.get().map
    lateinit var render: MapDrawer
    var latency_frames = 0
    var remaining_latency_frames = 0
    var frameCount = 0

//    private val combatManager = BehaviorTree(AttackEnemyBase())

    private lateinit var bot: Node<Any, Any>

    private lateinit var buildQueue: Node<Any, Any>

    fun start() {
        game.startGame()
    }

    override fun onStart() {
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
//        game.interactionHandler.sendText("black sheep wall")
//        game.interactionHandler.sendText("power overwhelming")

        Logger.getLogger("").level = Level.INFO

        val racePlayed = self.race
        when (racePlayed) {
            Race.Protoss -> FTTConfig.useConfigForProtoss()
            Race.Terran -> FTTConfig.useConfigForTerran()
            Race.Zerg -> FTTConfig.useConfigForZerg()
            else -> throw IllegalStateException("Can't handle race ${racePlayed}")
        }

        buildQueue = fallback(
                msequence("buildQueue",
//                ZvP._massZergling()
                        ZvP._2HatchMuta(),
                        Inline("Crap Check") {
                            NodeStatus.SUCCEEDED
                        }
                ),
                Inline("Crap Check") {
                    NodeStatus.SUCCEEDED
                }

        )
        bot = fallback(
                parallel(100,
//                Delegate {
//                    Combat.attack(UnitQuery.myUnits.filter { it is MobileUnit && it !is Worker && it is Attacker },
//                            UnitQuery.enemyUnits)
//                },
                        buildQueue,
                        NoFail(scout()),
//                fallback(sequence(
//                        Sleep(24),
//                        Delegate {
//                            Combat.defendPosition(UnitQuery.myUnits.filter { it !is Worker && it is Attacker }.filterIsInstance(MobileUnit::class.java),
//                                    self.startLocation.toPosition(), (game.bwMap.startPositions - self.startLocation)[0].toPosition())
//                        }
//                ), Sleep),
//                        msequence("",
//                                Condition { !EnemyInfo.enemyBases.isEmpty() },
//
//                                MDelegate
//                                {
//                                    Combat.moveToStandOffPosition(UnitQuery.myUnits.filter { it !is Worker && it is Attacker }.filterIsInstance(MobileUnit::class.java),
//                                            EnemyInfo.enemyBases[0].position)
//                                })
//                ),
                        NoFail(defending()),
                        NoFail(attacking()),
                        NoFail(preventSupplyBlock()),
                        NoFail(moveSurplusWorkers()),
                        NoFail(sequence(GatherResources, Sleep))
                ),
                Inline("This shouldn't have happened") {
                    LOG.error("Main loop failed!")
                    NodeStatus.SUCCEEDED
                }
        )
        UnitQuery.reset()

        EnemyInfo.reset()
    }

    override fun onFrame() {
        latency_frames = game.interactionHandler.latencyFrames
        remaining_latency_frames = game.interactionHandler.remainingLatencyFrames
        frameCount = game.interactionHandler.frameCount

//        val result = bwem.GetMap().GetPath(game.bwMap.startPositions[0].toPosition(), game.bwMap.startPositions[1].toPosition())

        if (game.interactionHandler.frameCount % latency_frames == 0) {
            UnitQuery.update(game.allUnits)
//        Exporter.export()
            EnemyInfo.step()
            Cluster.step()
            ClusterUnitInfo.step()

            Board.reset()
            bot.tick()
            if (UnitQuery.myWorkers.any { !it.exists() }) {
                throw IllegalStateException()
            }
        }
        bwem.neutralData.minerals.filter { mineral -> bwem.areas.none { area -> area.minerals.contains(mineral) } }
                .forEach {
                    game.mapDrawer.drawTextMap(it.center, "ua")
                }
        UnitQuery.myWorkers.forEach {
            game.mapDrawer.drawTextMap(it.position, "${it.id}(${it.lastCommand})")
            if (it.targetPosition != null) {
                game.mapDrawer.drawLineMap(it.position, it.targetPosition, Color.BLUE)
            }
        }

        EnemyInfo.seenUnits.forEach {
            game.mapDrawer.drawCircleMap(it.position, 16, Color.RED)
            game.mapDrawer.drawTextMap(it.position, "${it.initialType}")
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
        LOG.info("Completed: ${unit}")
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
//        ProcessHelper.killStarcraftProcess()
//        ProcessHelper.killChaosLauncherProcess()
        println()
        println("Exiting...")
//        System.exit(0)
    }
}

fun Double.or(other: Double) = if (isNaN()) other else this