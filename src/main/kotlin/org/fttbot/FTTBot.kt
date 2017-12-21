package org.fttbot

import bwapi.*
import bwapi.Unit
import bwta.BWTA
import com.badlogic.gdx.ai.btree.BehaviorTree
import org.fttbot.behavior.*
import org.fttbot.layer.FUnit
import org.fttbot.layer.FUnitType
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

    private val combatManager = BehaviorTree(AttackEnemyBase())

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
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_Barracks),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_SCV),
                ProductionBoard.Item(FUnitType.Terran_Barracks),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Supply_Depot),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine),
                ProductionBoard.Item(FUnitType.Terran_Marine)
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

        workerManager.step()
        buildManager.step()
        scoutManager.step()
        combatManager.step()
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
    }

    override fun onUnitDestroy(unit: Unit) {
        BBUnit.destroy(FUnit.of(unit))
        FUnit.destroy(unit)
    }

    override fun onUnitCreate(unit: Unit) {
        val funit = FUnit.of(unit)
        ProductionBoard.queueNeedsRebuild = true
        if (funit.isBuilding && funit.isPlayerOwned) {
            val workerWhoStartedIt = FUnit.myWorkers().firstOrNull {
                val construction = it.board.construction ?: return@firstOrNull false
                construction.commissioned && construction.position == unit.tilePosition && construction.type == funit.type
            }
            if (workerWhoStartedIt != null) {
                workerWhoStartedIt.board.construction?.started = true
            } else {
                LOG.severe("Can't find worker associated with building ${funit}!")
            }
        }
    }

    override fun onUnitComplete(unit: Unit) {
        val funit = FUnit.of(unit)
        if (funit.isPlayerOwned) {
            UnitBehaviors.createTreeFor(funit)
        }
    }

    override fun onUnitShow(unit: Unit) {
        FUnit.of(unit)
    }
}