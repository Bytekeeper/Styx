package org.fttbot

import bwapi.*
import bwapi.Unit
import bwta.BWTA
import com.badlogic.gdx.ai.btree.BehaviorTree
import org.fttbot.behavior.*
import org.fttbot.estimation.EnemyModel
import org.fttbot.import.FUnitType
import org.fttbot.layer.FUnit
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import java.util.logging.Logger

object FTTBot : DefaultBWListener() {
    private val LOG = Logger.getLogger(this::class.java.simpleName)
    private val mirror = Mirror()
    lateinit var game: Game
    lateinit var self: Player
    lateinit var enemy: Player
    private val workerManager = BehaviorTree(AssignWorkersToResources())
    private val buildManager = BehaviorTree(BuildNextItemFromProductionQueue(), ProductionBoard)
    private val scoutManager = BehaviorTree(ScoutEnemyBase(), ScoutingBoard)

//    private val combatManager = BehaviorTree(AttackEnemyBase())

    fun start() {
        mirror.module.setEventListener(this)
        mirror.startGame(true)
    }

    override fun onStart() {
        BWTA.readMap()
        BWTA.analyze()
        game = mirror.game
        self = game.self()
        enemy = game.enemy()

        Thread.sleep(100)
        mirror.game.setLocalSpeed(1)

        val consoleHandler = ConsoleHandler()
        consoleHandler.level = Level.INFO
        Logger.getLogger("").addHandler(consoleHandler)
        Logger.getLogger("").level = Level.INFO

        val racePlayed = game.self().race
        when (racePlayed) {
            Race.Protoss -> FTTConfig.useConfigForProtoss()
            Race.Terran -> FTTConfig.useConfigForTerran()
            Race.Zerg -> FTTConfig.useConfigForZerg()
        }

        ProductionBoard.queue.addAll(listOf(
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_Barracks),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_Refinery),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Factory),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Factory),
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture),
                ProductionBoard.Item(FUnitType.Terran_Vulture)
        ))
    }

    override fun onFrame() {
        while (game.isPaused) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                // No need to handle
            }
        }

//        Exporter.export()

        EnemyModel.step()
        workerManager.step()
        buildManager.step()
        scoutManager.step()
        UnitBehaviors.step()

        for (board in BBUnit.all()) {
            game.drawTextMap(board.unit.position, board.status)
        }

        BWTA.getRegions().forEachIndexed { index, region ->
            val poly = region.polygon.points
            for (i in 0 until poly.size) {
                val a = poly[i] //.toVector().scl(0.9f).mulAdd(region.polygon.center.toVector(), 0.1f).toPosition()
                val b = poly[(i + 1) % poly.size] //.toVector().scl(0.9f).mulAdd(region.polygon.center.toVector(), 0.1f).toPosition()
                game.drawLineMap(a, b,
                        when (index % 5) {
                            0 -> Color.Green
                            1 -> Color.Blue
                            2 -> Color.Brown
                            3 -> Color.Yellow
                            else -> Color.Grey
                        })
            }
            region.chokepoints.forEach { chokepoint ->
                game.drawLineMap(chokepoint.sides.first, chokepoint.sides.second, Color.Red)
            }
        }
        ConstructionPosition.resourcePolygons.values.forEach { poly ->
            val v = poly.vertices
            for (i in 0 until v.size / 2) {
                var j = i * 2
                val a = Position(v[j].toInt() * 32, v[j + 1].toInt() * 32)
                val b = Position(v[(j + 2) % v.size].toInt() * 32, v[(j + 3) % v.size].toInt() * 32)
                game.drawLineMap(a, b, Color.Cyan)
            }
        }
        for (unit in EnemyModel.seenUnits) {
            if (unit.position == null) continue
            game.drawCircleMap(unit.position, unit.type.width, Color.Red)
            game.drawTextMap(unit.position, unit.type.toString())
        }
        for (unit in FUnit.myUnits().filter { it.canAttack }) {
            game.drawTextMap(unit.position.translated(0, -10), "%.2f".format(unit.board.combatSuccessProbability))
        }
    }

    override fun onUnitDestroy(unit: Unit) {
        val fUnit = FUnit.of(unit)
        BBUnit.destroy(fUnit)
        UnitBehaviors.removeBehavior(fUnit)
        FUnit.destroy(unit)
        EnemyModel.remove(fUnit)
    }

    override fun onUnitCreate(unit: Unit) {
        if (unit.type.isNeutral) return
        val funit = FUnit.of(unit)
        ProductionBoard.queueNeedsRebuild = true
        checkForStartedConstruction(funit)
    }

    private fun checkForStartedConstruction(funit: FUnit) {
        if (funit.isBuilding && funit.isPlayerOwned) {
            val workerWhoStartedIt = FUnit.myWorkers().firstOrNull {
                val construction = it.board.construction ?: return@firstOrNull false
                construction.commissioned && construction.position == funit.tilePosition && construction.type == funit.type
            }
            if (workerWhoStartedIt != null) {
                val construction = workerWhoStartedIt.board.construction!!
                construction.building = funit
                construction.started = true
            } else {
                LOG.severe("Can't find worker associated with building ${funit}!")
            }
        }
    }

    override fun onUnitMorph(unit: Unit) {
        if (unit.type.isNeutral) return
        val funit = FUnit.of(unit)
        checkForStartedConstruction(funit)
    }

    override fun onUnitComplete(unit: Unit) {
        if (unit.type.isNeutral) return
        val funit = FUnit.of(unit)
        LOG.info("Completed: ${funit}")
        if (funit.isPlayerOwned && !UnitBehaviors.hasBehaviorFor(funit)) {
            UnitBehaviors.createTreeFor(funit)
        }
    }

    override fun onUnitShow(unit: Unit) {
        val fUnit = FUnit.of(unit)
        if (fUnit.isEnemy) EnemyModel.updateUnit(fUnit)
    }
}