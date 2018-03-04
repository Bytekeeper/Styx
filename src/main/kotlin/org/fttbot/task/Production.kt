package org.fttbot.task

import com.badlogic.gdx.scenes.scene2d.actions.Actions
import org.fttbot.*
import org.fttbot.FTTBot.frameCount
import org.fttbot.info.UnitQuery
import org.fttbot.info.findWorker
import org.fttbot.info.isMyUnit
import org.fttbot.search.MCTS
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import java.util.logging.Level
import java.util.logging.Logger


object BoSearch : Node {
    override fun tick(): NodeStatus {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private val LOG = Logger.getLogger(this::class.java.simpleName)
    private var mcts: MCTS? = null
    val utility: Double = 0.4
    private var relocateTo: MCTS.Node? = null

    fun run(available: Resources): NodeStatus {
        if (mcts == null) {
            mcts = MCTS(mapOf(UnitType.Terran_Marine to 1, UnitType.Terran_Vulture to 1, UnitType.Terran_Goliath to 1), setOf(), mapOf(UpgradeType.Ion_Thrusters to 1), Race.Terran)
        }
//        if (ProductionQueue.hasItems) return NodeStatus.RUNNING

        val searcher = mcts ?: throw IllegalStateException()
        if (PlayerUnit.getMissingUnits(UnitQuery.myUnits, UnitType.Terran_Marine.requiredUnits() + UnitType.Terran_Vulture.requiredUnits())
                        .isEmpty() && FTTBot.self.getUpgradeLevel(UpgradeType.Ion_Thrusters) > 0) return NodeStatus.SUCCEEDED
        val researchFacilities = UnitQuery.myUnits.filterIsInstance(ResearchingFacility::class.java)
        val upgradesInProgress = researchFacilities.map {
            val upgrade = it.upgradeInProgress
            upgrade.upgradeType to GameState.UpgradeState(-1, upgrade.remainingUpgradeTime, FTTBot.self.getUpgradeLevel(upgrade.upgradeType) + 1)
        }.toMap()
        val upgrades = UpgradeType.values()
                .filter { it.race == searcher.race }
                .map {
                    Pair(it, upgradesInProgress[it] ?: GameState.UpgradeState(-1, 0, FTTBot.self.getUpgradeLevel(it)))
                }.toMap().toMutableMap()
        val units = UnitQuery.myUnits.map {
            it.initialType to GameState.UnitState(-1,
                    if (!it.isCompleted && it is Building) it.remainingBuildTime
                    else if (it is TrainingFacility) it.remainingTrainTime
                    else 0)
        }.groupBy { (k, v) -> k }
                .mapValues { it.value.map { it.second }.toMutableList() }.toMutableMap()
        if (FTTBot.self.supplyUsed() != units.map { (k, v) -> k.supplyRequired() * v.size }.sum()) {
            LOG.warning("Supply used differs from supply actually used!")
            return NodeStatus.RUNNING
        }
        val researchInProgress = researchFacilities.map {
            val research = it.researchInProgress
            research.researchType to research.remainingResearchTime
        }
        val tech = (TechType.values()
                .filter { it.race == searcher.race && FTTBot.self.hasResearched(it) }
                .map { it to 0 } + Pair(TechType.None, 0) + researchInProgress).toMap().toMutableMap()

        val state = GameState(0, FTTBot.self.race, FTTBot.self.supplyUsed(), FTTBot.self.supplyTotal(), FTTBot.self.minerals(), FTTBot.self.gas(),
                units, tech, upgrades)

        val researchMove = searcher.root.children?.firstOrNull {
            if (it.move !is MCTS.UpgradeMove) return@firstOrNull false
            val upgrade = upgrades[it.move.upgrade] ?: return@firstOrNull false
            upgrade.level > 0 || upgrade.availableAt > 0
        }
        if (researchMove != null) {
            searcher.relocateTo(researchMove)
        }
        if (relocateTo != null && frameCount > 10) {
            searcher.relocateTo(relocateTo!!)
        }
        relocateTo = null

        try {
            searcher.restart()
            repeat(200) {
                try {
                    searcher.step(state)
                } catch (e: IllegalStateException) {
                    LOG.severe("Failed to search with $e")
                    searcher.restart()
                } catch (e: ArrayIndexOutOfBoundsException) {
                    LOG.severe("Failed to search with $e")
                    searcher.restart()
                }
            }

            val n = searcher.root.children?.minBy { it.frames }
            if (n != null) {
                val move = n.move ?: IllegalStateException()
                when (move) {
                    is MCTS.UnitMove -> {
//                        if (!ProductionQueue.hasEnqueued(move.unit)) if (!move.unit.isRefinery || !state.hasRefinery) ProductionQueue.enqueue(BOUnit(move.unit))
                        return NodeStatus.RUNNING
                    }
                    is MCTS.UpgradeMove -> {
//                        if (!ProductionQueue.hasEnqueued(move.upgrade)) ProductionQueue.enqueue(BOUpgrade(move.upgrade))
                        val level = FTTBot.self.getUpgradeLevel(move.upgrade)
                        return NodeStatus.RUNNING
                    }
                }
            }
            return NodeStatus.RUNNING
        } catch (e: IllegalStateException) {
            LOG.log(Level.SEVERE, "Couldn't determine build order, guess it's over", e)
        }
        return NodeStatus.FAILED
    }

    fun onUnitDestroy(unit: PlayerUnit) {
        if (unit.isMyUnit) {
            mcts?.restart()
        }
    }


    fun onUnitCreate(unit: PlayerUnit) {
        if (unit.isMyUnit && FTTBot.frameCount > 0) {
            val selectedMove = mcts?.root?.children?.firstOrNull { it.move is MCTS.UnitMove && unit.isA(it.move.unit) }
            if (selectedMove == null) {
                mcts?.restart()
            } else {
                relocateTo = selectedMove
            }
        }
    }

    fun onUnitRenegade(unit: PlayerUnit) {
        onUnitCreate(unit)
    }
}
