package org.fttbot

import bwta.BWTA
import com.badlogic.gdx.ai.btree.BehaviorTree
import com.badlogic.gdx.ai.btree.branch.Selector
import org.fttbot.behavior.*
import org.fttbot.estimation.EnemyModel
import org.fttbot.layer.UnitQuery
import org.fttbot.layer.board
import org.fttbot.layer.isEnemyUnit
import org.fttbot.layer.isMyUnit
import org.openbw.bwapi4j.*
import org.openbw.bwapi4j.type.Color
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.PlayerUnit
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger
import org.openbw.bwapi4j.unit.Unit

object FTTBot : BWEventListener {

    private val LOG = Logger.getLogger(this::class.java.simpleName)
    val game = BW(this)
    lateinit var self: Player
    lateinit var enemy: Player
    lateinit var bwta : BWTA
    lateinit var render: MapDrawer
    private val workerManager = BehaviorTree(AssignWorkersToResources())
    private val buildManager = BehaviorTree(
            Selector (
                    SupplyProduction(),
                    BuildNextItemFromProductionQueue(),
                    ProduceAttacker(), WorkerProduction()
            ),
            ProductionBoard)
    private val scoutManager = BehaviorTree(ScoutEnemyBase(), ScoutingBoard)

//    private val combatManager = BehaviorTree(AttackEnemyBase())

    fun start() {
        game.startGame()
    }

    override fun onStart() {
        bwta = BWTA()
        bwta.analyze()

        self = game.interactionHandler.self()
        enemy = game.interactionHandler.enemy()
        render = game.mapDrawer

        Thread.sleep(100)
        game.interactionHandler.setLocalSpeed(1)

        val consoleHandler = ConsoleHandler()
        consoleHandler.level = Level.INFO
        Logger.getLogger("").addHandler(consoleHandler)
        Logger.getLogger("").level = Level.INFO

        val racePlayed = self.race
        when (racePlayed) {
            Race.Protoss -> FTTConfig.useConfigForProtoss()
            Race.Terran -> FTTConfig.useConfigForTerran()
            Race.Zerg -> FTTConfig.useConfigForZerg()
            else -> throw IllegalStateException("Can't handle race ${racePlayed}")
        }

        ProductionBoard.queue.addAll(listOf(
                ProductionBoard.Item(UnitType.Terran_SCV),
                ProductionBoard.Item(UnitType.Terran_SCV),
                ProductionBoard.Item(UnitType.Terran_SCV),
                ProductionBoard.Item(UnitType.Terran_SCV),
                ProductionBoard.Item(UnitType.Terran_Barracks),
                ProductionBoard.Item(UnitType.Terran_SCV),
                ProductionBoard.Item(UnitType.Terran_Supply_Depot),
                ProductionBoard.Item(UnitType.Terran_SCV),
                ProductionBoard.Item(UnitType.Terran_Refinery),
                ProductionBoard.Item(UnitType.Terran_Marine),
                ProductionBoard.Item(UnitType.Terran_SCV),
                ProductionBoard.Item(UnitType.Terran_Marine),
                ProductionBoard.Item(UnitType.Terran_Factory),
                ProductionBoard.Item(UnitType.Terran_Machine_Shop),
                ProductionBoard.Item(UnitType.Terran_Factory),
                ProductionBoard.Item(UnitType.Terran_Siege_Tank_Tank_Mode)
        ))
    }

    override fun onFrame() {
        UnitQuery.update(game.allUnits)
//        Exporter.export()
        EnemyModel.step()
        workerManager.step()
        ProductionBoard.updateReserved()
        buildManager.step()
        scoutManager.step()
        UnitBehaviors.step()

        for (board in BBUnit.all()) {
            render.drawTextMap(board.unit.position, board.status)
        }

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
        ConstructionPosition.resourcePolygons.values.forEach { poly ->
            val v = poly.vertices
            for (i in 0 until v.size / 2) {
                var j = i * 2
                val a = Position(v[j].toInt() * 32, v[j + 1].toInt() * 32)
                val b = Position(v[(j + 2) % v.size].toInt() * 32, v[(j + 3) % v.size].toInt() * 32)
                render.drawLineMap(a, b, Color.CYAN)
            }
        }
        for (unit in UnitQuery.enemyUnits.filter { !it.isVisible }) {
            if (unit.position == null) continue
            render.drawCircleMap(unit.position, unit.width(), Color.RED)
            render.drawTextMap(unit.position, unit.toString())
        }
    }

    override fun onUnitDestroy(unit: Unit) {
        if (unit !is PlayerUnit) return
        BBUnit.destroy(unit)
        UnitBehaviors.removeBehavior(unit)
    }

    override fun onUnitCreate(unit: Unit) {
        if (unit !is PlayerUnit) return
        checkForStartedConstruction(unit)
    }

    private fun checkForStartedConstruction(unit: Unit) {
        if (unit is Building && unit.isMyUnit) {
            val workerWhoStartedIt = UnitQuery.myWorkers.firstOrNull {
                val construction = it.board.construction ?: return@firstOrNull false
                construction.commissioned && construction.position == unit.tilePosition && unit.isA(construction.type)
            }
            if (workerWhoStartedIt != null) {
                val construction = workerWhoStartedIt.board.construction!!
                construction.building = unit
                construction.started = true
            } else {
                LOG.severe("Can't find worker associated with building ${unit}!")
            }
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
        if (unit is PlayerUnit && unit.isEnemyUnit) EnemyModel.updateUnit(unit)
    }

    override fun onUnitHide(unit: Unit?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUnitRenegade(unit: Unit?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUnitDiscover(unit: Unit?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPlayerLeft(player: Player?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSendText(text: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onReceiveText(player: Player?, text: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onNukeDetect(target: Position?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSaveGame(gameName: String?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onUnitEvade(unit: Unit?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onEnd(isWinner: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}