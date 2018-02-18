package org.fttbot

import bwem.BWEM
import bwta.BWTA
import org.fttbot.StrategyUtility.needMobileDetection
import org.fttbot.behavior.*
import org.fttbot.estimation.BOPrediction
import org.fttbot.estimation.UnitVsUnit
import org.fttbot.info.*
import org.fttbot.task.*
import org.openbw.bwapi4j.*
import org.openbw.bwapi4j.type.Color
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
                BOUnit(UnitType.Terran_SCV),
                BOUnit(UnitType.Terran_SCV),
                BOUnit(UnitType.Terran_SCV),
                BOUnit(UnitType.Terran_SCV)
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

            Task.step()
            UnitBehaviors.step()
        }

        if (bwtaInitializer.isDone) {
            bwta.getRegions().forEachIndexed { index, region ->
                val poly = region.polygon.points
                for (i in 0 until poly.size) {
                    val a = poly[i] //.toVector().scl(0.9f).mulAdd(region.polygon.center.toVector(), 0.1f).toPosition()
                    val b = poly[(i + 1) % poly.size] //.toVector().scl(0.9f).mulAdd(region.polygon.center.toVector(), 0.1f).toPosition()
                    render.drawLineMap(a, b,
                            when (index % 5) {
                                0 -> Color.GREEN
                                1 -> Color.BLUE
                                2 -> Color.BROWN
                                3 -> Color.YELLOW
                                else -> Color.GREY
                            })
                }
                region.chokepoints.forEach { chokepoint ->
                    render.drawLineMap(chokepoint.sides.first, chokepoint.sides.second, Color.RED)
                }
            }
        }
        ConstructionPosition.resourcePolygons.values.forEach { poly ->
            val v = poly.vertices
            for (i in 0 until v.size / 2) {
                var j = i * 2
                val a = Position(v[j].toInt() * 32, v[j + 1].toInt() * 32)
                val b = Position(v[(j + 2) % v.size].toInt() * 32, v[(j + 3) % v.size].toInt() * 32)
                render.drawLineMap(a, b, Color.CYAN)
            }
        }
        for (unit in UnitQuery.myUnits) {
            val b = unit.userData as? BBUnit ?: continue
            render.drawTextMap(unit.position, "${unit.id}: ${b.status}")
            render.drawTextMap(unit.position + Position(0, 10), "a:${UnitUtility.attack(b)} ct:${UnitUtility.collateralThreat(b)}")
            render.drawTextMap(unit.position + Position(0, 20), "r:${UnitUtility.runaway(b)} it:${UnitUtility.immediateThreat(unit)}")
//            render.drawTextMap(unit.position + Position(0, -10), "% ${b.combatSuccessProbability}")
            val util = b.utility
//            render.drawTextMap(unit.position + Position(0, -40), "a ${util.attack}, d ${util.defend}, f ${util.force}")
//            render.drawTextMap(unit.position + Position(0, -30), "t ${util.collateralThreat}, v ${util.worth}, c ${util.construct}")
//            render.drawTextMap(unit.position + Position(0, -20), "g ${util.gather}")
            val goal = b.goal
            when (goal) {
                is Attacking -> if (b.target != null) render.drawLineMap(unit.position, b.target!!.position, Color.RED)
                is Construction -> render.drawLineMap(unit.position, goal.position.toPosition() + Position(16, 16), Color.GREY)
                is Repairing -> render.drawLineMap(unit.position, (goal.target as Unit).position + Position(16, 16), Color.GREY)
            }
            b.moveTarget?.let {
                render.drawLineMap(unit.position, it, Color.BLUE)
            }
        }
        for (unit in UnitQuery.enemyUnits.filter { !it.isVisible }) {
            if (unit.position == null) continue
            render.drawCircleMap(unit.position, unit.width(), Color.RED)
            render.drawTextMap(unit.position, unit.toString())
        }
        for (unit in EnemyState.seenUnits) {
            render.drawCircleMap(unit.position, unit.width(), Color.YELLOW)
            render.drawTextMap(unit.position, unit.toString())
        }
        Cluster.enemyClusters.forEach {
            render.drawCircleMap(it.position, 300, Color.RED)
        }
        Cluster.mobileCombatUnits.forEach {
            render.drawCircleMap(it.position, 300, Color.ORANGE)
            val combat = ClusterUnitInfo.getInfo(it)
            val prob = combat.combatEval
            render.drawTextMap(it.position + Position(0, -200), "$prob, # ${combat.combatRelevantUnits.size} / ${combat.unitsInArea.size}")
        }
        Task.lastTaskOrder.forEachIndexed { index, task -> render.drawTextScreen(0, 60 + 10 * index, "${task.javaClass.simpleName} : ${task.utility}") }

        val t = System.currentTimeMillis()
        render.drawTextScreen(0, 20, "${System.currentTimeMillis() - t} ms")
        render.drawTextScreen(0, 30, "needDetection: ${StrategyUtility.needMobileDetection()}")
        val predict = BOPrediction.predict()
        if (predict != null) {
            render.drawTextScreen(0, 40, "${predict.category} : ${predict.probability}")
            val bestVs = UnitVsUnit.bestUnitVs(UnitTypes.trainables, predict.category)
            render.drawTextScreen(0, 50, "Best I could build: $bestVs")
        }
    }

    override fun onUnitDestroy(unit: Unit) {
        if (unit !is PlayerUnit) return
        UnitBehaviors.removeBehavior(unit)
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
        if (unit.isMyUnit && !UnitBehaviors.hasBehaviorFor(unit)) {
            UnitBehaviors.createTreeFor(unit)
        }
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