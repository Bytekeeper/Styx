package org.fttbot.task

import org.fttbot.*
import org.fttbot.FTTBot.frameCount
import org.fttbot.info.UnitQuery
import org.fttbot.info.findWorker
import org.fttbot.info.isMyUnit
import org.fttbot.search.MCTS
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import java.util.logging.Level
import java.util.logging.Logger


class ConstructionStarted(val worker: Worker, val type: UnitType, val at: TilePosition) : Node {
    var started = false

    override fun tick(): NodeStatus {
        if (started) {
            val candidate = UnitQuery.myUnits.firstOrNull { it is Building && it.tilePosition == at }
                    ?: return NodeStatus.RUNNING
            if (candidate.isA(type))
                return NodeStatus.SUCCEEDED
            return NodeStatus.FAILED
        }
        if (worker.buildType != UnitType.None) {
            started = true;
        }
        return NodeStatus.RUNNING
    }
}


object Production {
    fun buildWithWorker(worker: Worker, at: TilePosition, building: UnitType): Node {
        return OneOf(
                ConstructionStarted(worker, building, at),
                Sequence(
                        ReserveUnit(worker),
                        Fallback(ReserveResources(building.mineralPrice(), building.gasPrice()), Sleep),
                        MSequence("executeBuild",
                                CompoundActions.reach(worker, at.toPosition(), 150),
                                Build(worker, at, building),
                                Await(48) { worker.isConstructing },
                                Sleep(48))
                ))
    }

    fun selectTrainer(currentTrainer: PlayerUnit?, unit: UnitType, near: Position?): PlayerUnit? {
        if (currentTrainer?.exists() == true && (currentTrainer !is TrainingFacility || !currentTrainer.isTraining)) return currentTrainer
        return Board.resources.units
                .filter {
                    it.isA(unit.whatBuilds().first)
                            && (it !is TrainingFacility || !it.isTraining)
                }
                .minBy { near?.getDistance(it.position) ?: 0 }
    }

    fun selectResearcher(currentResearcher: PlayerUnit?, tech: TechType): PlayerUnit? {
        if (currentResearcher?.exists() == true && !(currentResearcher as ResearchingFacility).isResearching
                && !(currentResearcher as ResearchingFacility).isUpgrading) return currentResearcher
        return Board.resources.units
                .firstOrNull {
                    it.isA(tech.whatResearches())
                            && it is ResearchingFacility
                            && !it.isResearching
                            && !it.isUpgrading
                }
    }

    fun selectResearcher(currentResearcher: PlayerUnit?, upgrade: UpgradeType): PlayerUnit? {
        if (currentResearcher?.exists() == true && !(currentResearcher as ResearchingFacility).isResearching
                && !(currentResearcher as ResearchingFacility).isUpgrading) return currentResearcher
        return Board.resources.units
                .firstOrNull {
                    it.isA(upgrade.whatUpgrades())
                            && it is ResearchingFacility
                            && !it.isResearching
                            && !it.isUpgrading
                }
    }

    fun build(type: UnitType, at: () -> TilePosition? = { null }): Node {
        require(type.isBuilding && !type.isAddon)
        var targetPosition: TilePosition? = null
        var builder: PlayerUnit? = null
        return Retry(3, MSequence("build",
                Inline {
                    targetPosition = null
                    builder = null
                    NodeStatus.SUCCEEDED
                },
                ensureDependencies(type),
                Sequence(
                        BlockResources(type.mineralPrice(), type.gasPrice()),
                        Await { FTTBot.self.canMake(type) },
                        Inline {
                            targetPosition = targetPosition ?: at() ?: ConstructionPosition.findPositionFor(type)
                                    ?: return@Inline NodeStatus.FAILED
                            builder = if (builder != null && Board.resources.units.contains(builder!!)) builder else
                                if (type.whatBuilds().first.isWorker)
                                    findWorker(targetPosition!!.toPosition(), candidates = Board.resources.units.filterIsInstance(Worker::class.java))
                                else
                                    Board.resources.units.firstOrNull { it.isA(type.whatBuilds().first) && it is Building && it.remainingBuildTime == 0 }
                                            ?: return@Inline NodeStatus.FAILED
                            NodeStatus.SUCCEEDED
                        }
                ),
                Inline {
                    Board.pendingUnits += type
                    NodeStatus.SUCCEEDED
                },
                MDelegate
                {
                    if (builder is Worker)
                        buildWithWorker(builder as Worker, targetPosition!!, type)
                    else
                        morph(builder as Morphable, type)
                }
        ))
    }

    fun morph(unit: Morphable, to: UnitType): Node {
        return MSequence("morph",
                Sequence(
                        ReserveUnit(unit as PlayerUnit),
                        Fallback(ReserveResources(to.mineralPrice(), to.gasPrice()), Sleep)
                ),
                MDelegate {
                    MorphCommand(unit, to)
                }
        )
    }

    fun train(unit: UnitType, near: () -> TilePosition? = { null }): Node {
        require(!unit.isBuilding || unit.isAddon)
        var trainer: PlayerUnit? = null
        val supplyUsed = if (unit == UnitType.Zerg_Zergling) 2 * unit.supplyRequired() else unit.supplyRequired()
        return MSequence("train",
                ensureDependencies(unit),
                Sequence(
                        All("ensureResources",
                                ensureSupply(supplyUsed),
                                Fallback(ReserveResources(unit.mineralPrice(), unit.gasPrice()), Sleep)
                        ),
                        Await { FTTBot.self.canMake(unit) },
                        Inline {
                            trainer = selectTrainer(trainer, unit, near()?.toPosition())
                                    ?: return@Inline NodeStatus.RUNNING
                            NodeStatus.SUCCEEDED
                        },
                        Delegate { ReserveUnit(trainer!!) },
                        Inline {
                            Board.pendingUnits.add(unit)
                            NodeStatus.SUCCEEDED
                        },
                        MDelegate {
                            when (trainer) {
                                is Morphable -> MorphCommand(trainer as Morphable, unit)
                                else -> TrainCommand(trainer as TrainingFacility, unit)
                            }
                        }
                ),
                Inline {
                    if (!trainer!!.exists()) {
                        trainer = UnitQuery.myUnits.firstOrNull { it.id == trainer!!.id } ?: return@Inline NodeStatus.FAILED
                    }
                    if (trainer?.isA(unit) == true)
                        return@Inline NodeStatus.SUCCEEDED
                    NodeStatus.RUNNING
                }
        )
    }

    fun ensureUnitDependencies(dependencies: List<UnitType>): Node {
        return MMapAll({
            val missing = PlayerUnit.getMissingUnits(UnitQuery.myUnits, dependencies).toMutableList()
            if (dependencies.any { it != UnitType.Zerg_Larva && it.gasPrice() > 0 && it.gasPrice() > Board.resources.gas })
                missing.add(0, FTTConfig.GAS_BUILDING)
            missing -= Board.pendingUnits
            missing -= UnitType.Zerg_Larva
            missing -= UnitType.None
            missing.removeIf { type -> UnitQuery.myUnits.any { it.isA(type) } }
            missing

        }, "ensureDependencies")
        { produce(it) }
    }

    fun ensureResearchDependencies(tech: TechType): Node {
        if (tech == TechType.None) return Success
        return MSequence("resDep",
                ensureDependencies(tech.requiredUnit()),
                research(tech)
        )
    }

    fun ensureDependencies(unit: UnitType): Node {
        if (unit == UnitType.None) return Success
        return All("ensureDependencies",
                MDelegate { ensureUnitDependencies(unit.requiredUnits()) },
                ensureResearchDependencies(unit.requiredTech())
        )
    }

    fun ensureSupply(supplyDemand: Int): Fallback {
        return Fallback(
                ReserveSupply(supplyDemand),
                Require {
                    UnitQuery.myUnits.any {
                        !it.isCompleted && it is SupplyProvider
                                || it is Egg && it.buildType.supplyProvided() > 0
                    }
                },
                MDelegate { buildOrTrainSupply() }
        )
    }

    fun research(tech: TechType): Node {
        if (tech == TechType.None) return Success
        var researcher: PlayerUnit? = null
        return MSequence("research",
                ensureUnitDependencies(listOf(tech.requiredUnit())),
                Sequence(
                        Fallback(ReserveResources(tech.mineralPrice(), tech.gasPrice()), Sleep),
                        Await { FTTBot.self.canResearch(tech) }
                ),
                Inline {
                    researcher = selectResearcher(researcher, tech)
                            ?: return@Inline NodeStatus.RUNNING
                    NodeStatus.SUCCEEDED
                },
                Delegate { ReserveUnit(researcher!!) },
                MDelegate {
                    ResearchCommand(researcher as ResearchingFacility, tech)
                }
        )
    }

    fun upgrade(upgradeType: UpgradeType): Node {
        if (upgradeType == UpgradeType.None) return Success
        var researcher: PlayerUnit? = null
        val level = FTTBot.self.getUpgradeLevel(upgradeType)
        return Fallback(Require { FTTBot.self.getUpgradeLevel(upgradeType) > level },
                Sequence(
                        MDelegate { ensureUnitDependencies(listOf(upgradeType.whatsRequired(level), upgradeType.whatUpgrades())) },
                        MDelegate { Fallback(ReserveResources(upgradeType.mineralPrice(level), upgradeType.gasPrice(level)), Sleep) },
                        Await { FTTBot.self.canUpgrade(upgradeType) },
                        Inline {
                            researcher = selectResearcher(researcher, upgradeType)
                                    ?: return@Inline NodeStatus.RUNNING
                            NodeStatus.SUCCEEDED
                        },
                        Delegate { ReserveUnit(researcher!!) },
                        MDelegate {
                            UpgradeCommand(researcher as ResearchingFacility, upgradeType)
                        }
                ))
    }

    fun produce(unit: UnitType, near: () -> TilePosition? = { null }): Node =
            if (unit.isBuilding && !unit.isAddon)
                build(unit, near)
            else
                train(unit, near)


    fun buildOrTrainSupply(near: () -> TilePosition? = { null }): Node = produce(FTTConfig.SUPPLY)

    fun trainWorker(near: () -> TilePosition? = { null }): Node = train(FTTConfig.WORKER, near)
}

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
            LOG.log(Level.SEVERE, "Couldn't determine buildWithWorker order, guess it's over", e)
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
