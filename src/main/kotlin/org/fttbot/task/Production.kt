package org.fttbot.task

import org.fttbot.*
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


class AwaitConstructionStart(val worker: Worker, val type: UnitType, val at: TilePosition) : Node {
    var started = false

    override fun tick(): NodeStatus {
        if (started) {
            if (!worker.isConstructing)
                return NodeStatus.FAILED
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
                AwaitConstructionStart(worker, building, at),
                Sequence(
                        ReserveUnit(worker),
                        Fallback(ReserveResources(building.mineralPrice(), building.gasPrice()), Sleep),
                        MSequence("executeBuild",
                                CompoundActions.reach(worker, at.toPosition(), 150),
                                BuildCommand(worker, at, building),
                                Await(150) { worker.isConstructing },
                                Sleep(48))
                ))
    }

    fun selectTrainer(currentTrainer: PlayerUnit?, unit: UnitType, near: Position?): PlayerUnit? {
        val trainerType = unit.whatBuilds().first
        if (currentTrainer?.exists() == true
                && currentTrainer.isA(trainerType)
                && (currentTrainer !is TrainingFacility || !currentTrainer.isTraining)) return currentTrainer
        return Board.resources.units
                .filter {
                    it.isA(trainerType)
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
        return Retry(5, MSequence("build $type",
                Inline {
                    targetPosition = null
                    builder = null
                    NodeStatus.SUCCEEDED
                },
                Sequence(
                        ensureDependencies(type),
                        BlockResources(type.mineralPrice(), type.gasPrice()),
                        Await { FTTBot.self.canMake(type) },
                        Inline {
                            targetPosition = targetPosition ?: ConstructionPosition.findPositionFor(type, at()?.toPosition()) ?: ConstructionPosition.findPositionFor(type)
                                    ?: return@Inline NodeStatus.FAILED
                            builder = if (builder != null && Board.resources.units.contains(builder!!)) builder else
                                if (type.whatBuilds().first.isWorker)
                                    findWorker(targetPosition!!.toPosition(), 10000.0)
                                else
                                    Board.resources.units.firstOrNull { it.isA(type.whatBuilds().first) && it is Building && it.remainingBuildTime == 0 }
                                            ?: return@Inline NodeStatus.RUNNING
                            NodeStatus.SUCCEEDED
                        }
                ),
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
        return MSequence("morph $to",
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
        return MSequence("train $unit",
                ensureDependencies(unit),
                Sequence(
                        Inline {
                            Board.pendingUnits.add(unit)
                            NodeStatus.SUCCEEDED
                        },
                        All("ensureResources",
                                ensureSupply(supplyUsed),
                                Fallback(ReserveResources(unit.mineralPrice(), unit.gasPrice()), Sleep)
                        ),
                        Await { FTTBot.self.canMake(unit) },
                        Inline {
                            trainer = selectTrainer(trainer, unit, near()?.toPosition())
                                    ?: return@Inline NodeStatus.RUNNING
                            NodeStatus.SUCCEEDED
                        }
                ),
                Sequence(
                        MDelegate { ReserveUnit(trainer!!) },
                        MDelegate {
                            when (trainer) {
                                is Morphable -> MorphCommand(trainer as Morphable, unit)
                                else -> TrainCommand(trainer as TrainingFacility, unit)
                            }
                        }
                ),
                Await(48) {
                    if (!trainer!!.exists()) {
                        trainer = UnitQuery.myUnits.firstOrNull { it.id == trainer!!.id } ?: return@Await false
                    }
                    trainer?.isA(unit) == true || (trainer as? Egg)?.buildType == unit
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
                Require { enoughSupplyInConstruction() },
                MDelegate { buildOrTrainSupply() }
        )
    }

    private fun enoughSupplyInConstruction(): Boolean {
        return (UnitQuery.myUnits
                .filter {
                    !it.isCompleted && it is SupplyProvider
                            || it is Egg && it.buildType.supplyProvided() > 0
                }
                .sumBy {
                    ((it as? SupplyProvider)?.supplyProvided() ?: 0) +
                            ((it as? Egg)?.buildType?.supplyProvided() ?: 0)
                } + Board.resources.supply) >= 0
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

    fun trainWorker(near: () -> TilePosition? = { bestPositionForNewWorker() }): Node = train(FTTConfig.WORKER, near)

    fun cancel(type: UnitType): Node {
        require(type.isBuilding)
        var target: Building? = null
        return Sequence(
                Inline {
                    target = UnitQuery.myUnits.filterIsInstance(Building::class.java)
                            .firstOrNull { it.isA(type) && !it.isCompleted }
                            ?: return@Inline NodeStatus.FAILED
                    NodeStatus.SUCCEEDED
                },
                MDelegate { CancelCommand(target!!) }
        )
    }

    fun cancelGas() = cancel(FTTConfig.GAS_BUILDING)

    fun bestPositionForNewWorker(): TilePosition =
            (Info.myBases.minBy {
                it as PlayerUnit
                it.getUnitsInRadius(300, UnitQuery.myWorkers).size
            } as PlayerUnit).tilePosition

    fun buildGas(near: () -> TilePosition? = { null }): Node = build(FTTConfig.GAS_BUILDING)
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

        val state = GameState.fromCurrent()

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
