package org.fttbot

import bwapi.*
import bwapi.Unit
import com.badlogic.gdx.ai.btree.BehaviorTree
import org.fttbot.behavior.*
import org.fttbot.layer.FUnit
import org.fttbot.layer.FUnitType

object FTTBot : DefaultBWListener() {
    private val mirror = Mirror()
    lateinit var game: Game
    lateinit var self: Player;
    val workerManager = BehaviorTree(AssignWorkersToResources())
    val buildManager = BehaviorTree(BuildNextItemFromProductionQueue(), Production)

    fun start() {
        mirror.module.setEventListener(this)
        mirror.startGame(true)
    }

    override fun onStart() {
        game = mirror.game
        self = game.self()

        Thread.sleep(100);
        mirror.game.setLocalSpeed(1);

        val racePlayed = game.self().race
        if (racePlayed == Race.Protoss) {
            FTTConfig.useConfigForProtoss()
        } else if (racePlayed == Race.Terran) {
            FTTConfig.useConfigForTerran()
        } else if (racePlayed == Race.Zerg) {
            FTTConfig.useConfigForZerg()
        }

        Production.queue.addAll(listOf(
                Production.Item(FUnitType.Terran_SCV),
                Production.Item(FUnitType.Terran_SCV),
                Production.Item(FUnitType.Terran_SCV),
                Production.Item(FUnitType.Terran_Supply_Depot),
                Production.Item(FUnitType.Terran_SCV),
                Production.Item(FUnitType.Terran_Barracks),
                Production.Item(FUnitType.Terran_SCV),
                Production.Item(FUnitType.Terran_SCV),
                Production.Item(FUnitType.Terran_SCV),
                Production.Item(FUnitType.Terran_SCV),
                Production.Item(FUnitType.Terran_Barracks),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Supply_Depot),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Supply_Depot),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Supply_Depot),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Supply_Depot),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Marine),
                Production.Item(FUnitType.Terran_Supply_Depot)
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
        UnitBehaviors.step()
    }

    override fun onUnitDestroy(unit: Unit) {
        FUnit.destroy(unit)
    }

    override fun onUnitCreate(unit: Unit) {
        val funit = FUnit.of(unit)
        Production.queueNeedsRebuild = true
        if (funit.isBuilding) {
            FUnit.myWorkers()
                    .any { worker ->
                        val construction = BBUnit.of(worker).order as? Construction ?: return@any false
                        val position = construction.position ?: return@any false
                        if (position.equals(unit.tilePosition)) {
                            construction.started = true
                            true
                        } else {
                            false
                        }
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