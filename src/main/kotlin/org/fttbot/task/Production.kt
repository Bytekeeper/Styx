package org.fttbot.task

import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.MSequence.Companion.msequence
import org.fttbot.Parallel.Companion.parallel
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.*
import org.fttbot.info.MyInfo.pendingSupply
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


class AwaitConstructionStart(val worker: Worker, val type: UnitType, val at: TilePosition) : BaseNode() {
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
        } else if (!worker.exists()) {
            return NodeStatus.FAILED
        }
        if (worker.buildType != UnitType.None) {
            started = true;
        }
        return NodeStatus.FAILED
    }

    override fun parentFinished() {
        started = false
    }

    override fun toString(): String = "Awaiting start of $type by $worker"
}

object Production {
    fun buildWithWorker(worker: Worker, at: TilePosition, building: UnitType, mindSafety: Boolean): Node {
        return parallel(1,
                AwaitConstructionStart(worker, building, at),
                sequence(
                        ReserveUnit(worker),
                        fallback(ReserveResources(building.mineralPrice(), building.gasPrice()), sequence(Sleep(48), Fail)),
                        msequence("executeBuild",
                                if (mindSafety)
                                    Actions.reachSafely(worker, at.toPosition(), 150)
                                else
                                    Actions.reach(worker, at.toPosition(), 150),
                                BuildCommand(worker, at, building),
                                Await("worker constructing", 10) { worker.isConstructing },
                                MaxTries("Wait for construction start", 100,
                                        sequence(Condition("Still constructing?") { worker.isConstructing },
                                                Sleep)
                                )
                        )
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
                .minBy {
                    near?.getDistance(it.position)
                            ?: MyInfo.myBases.map { b -> b as PlayerUnit; b.getDistance(it) }.min()
                            ?: 0
                }
    }

    fun selectResearcher(currentResearcher: PlayerUnit?, tech: TechType): PlayerUnit? {
        if (currentResearcher?.exists() == true && !(currentResearcher as ResearchingFacility).isResearching
                && !(currentResearcher as ResearchingFacility).isUpgrading
                && Board.resources.units.contains(currentResearcher)) return currentResearcher
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
                && !(currentResearcher as ResearchingFacility).isUpgrading
                && Board.resources.units.contains(currentResearcher)) return currentResearcher
        return Board.resources.units
                .firstOrNull {
                    it.isA(upgrade.whatUpgrades())
                            && it is ResearchingFacility
                            && !it.isResearching
                            && !it.isUpgrading
                }
    }

    fun build(type: UnitType, considerSafety: Boolean? = null, at: () -> TilePosition? = { null }): Node {
        require(type.isBuilding && !type.isAddon)
        val safety = considerSafety ?: type.isResourceDepot
        var targetPosition: TilePosition? = null
        var builder: PlayerUnit? = null
        return Retry(15, msequence("build $type",
                sequence(
                        Inline("Reset") {

                            NodeStatus.SUCCEEDED
                        },
                        Inline("Mark $type as pending") {
                            Board.pendingUnits.add(type)
                            NodeStatus.SUCCEEDED
                        },
                        parallel(2,
                                ensureDependencies(type),
                                fallback(ReserveResources(type.mineralPrice(), type.gasPrice()), Sleep)
                        ),
                        Await("canMake $type") { FTTBot.self.canMake(type) },
                        Inline("find build location and builder") {
                            targetPosition = targetPosition ?: ConstructionPosition.findPositionFor(type, at()?.toPosition()) ?: ConstructionPosition.findPositionFor(type)
                                    ?: return@Inline NodeStatus.FAILED
                            builder = (if (builder != null && Board.resources.units.contains(builder!!)) builder else
                                if (type.whatBuilds().first.isWorker) {
                                    var workersToConsider = Board.resources.units.filterIsInstance(Worker::class.java)
                                    val buildPosition = targetPosition!!.toPosition()
                                    if (safety) {
                                        workersToConsider = workersToConsider.filter { Actions.canReachSafely(it, buildPosition) }
                                    }
                                    findWorker(buildPosition, 10000.0, workersToConsider)
                                } else
                                    Board.resources.units.firstOrNull { it.isA(type.whatBuilds().first) && it is Building && it.remainingBuildTime == 0 }
                                    ) ?: return@Inline NodeStatus.RUNNING
                            NodeStatus.SUCCEEDED
                        },
                        ReleaseResources(type.mineralPrice(), type.gasPrice())
                ),
                sequence(
                        Condition("Safe to build or don't care?") {
                            !safety || builder !is MobileUnit || Actions.canReachSafely(builder as Worker, targetPosition!!.toPosition())
                        },
                        Condition("Target position available?") {
                            if (!builder!!.exists() || builder !is Worker || FTTBot.game.canBuildHere(targetPosition, type, builder as Worker)) {
                                return@Condition true
                            }
                            targetPosition = null
                            return@Condition false
                        },
                        Delegate
                        {
                            if (builder is Worker)
//                        buildWithWorker(builder as Worker, targetPosition!!, type)
                                buildWithWorker(builder as Worker, targetPosition!!, type, safety)
                            else
                                morph(builder as Morphable, type)
                        }
                )
        ))
    }

    fun morph(unit: Morphable, to: UnitType): Node {
        return msequence("morph $to",
                sequence(
                        ReserveUnit(unit as PlayerUnit),
                        fallback(ReserveResources(to.mineralPrice(), to.gasPrice()), Sleep)
                ),
                Delegate {
                    MorphCommand(unit, to)
                }
        )
    }

    fun train(unit: UnitType, near: () -> TilePosition? = { null }): Node {
        require(!unit.isBuilding || unit.isAddon)
        var trainer: PlayerUnit? = null
        val supplyUsed = if (unit.isTwoUnitsInOneEgg) 2 * unit.supplyRequired() else unit.supplyRequired()
        return Retry(50,
                msequence("train $unit",
                        Inline("reset") {
                            trainer = null
                            NodeStatus.SUCCEEDED
                        },
                        sequence(
                                Inline("Mark $unit as pending") {
                                    Board.pendingUnits.add(unit)
                                    NodeStatus.SUCCEEDED
                                },
                                parallel(3,
                                        ensureDependencies(unit),
                                        ensureSupply(supplyUsed),
                                        fallback(ReserveResources(unit.mineralPrice(), unit.gasPrice()), Sleep)
                                ),
                                Await("can make $unit") { FTTBot.self.canMake(unit) },
                                Inline("Finding trainer for $unit") {
                                    trainer = selectTrainer(trainer, unit, near()?.toPosition())
                                            ?: return@Inline NodeStatus.RUNNING
                                    NodeStatus.SUCCEEDED
                                },
                                ReleaseResources(unit.mineralPrice(), unit.gasPrice(), unit.supplyRequired())
                        ),
                        sequence(
                                ReserveResources(unit.mineralPrice(), unit.gasPrice(), unit.supplyRequired()),
                                Delegate { ReserveUnit(trainer!!) },
                                Delegate {
                                    when (trainer) {
                                        is Morphable -> MorphCommand(trainer as Morphable, unit)
                                        else -> TrainCommand(trainer as TrainingFacility, unit)
                                    }
                                }
                        ),
                        Await("trainer to train", 48) {
                            if (!trainer!!.exists()) {
                                trainer = UnitQuery.myUnits.firstOrNull { it.id == trainer!!.id } ?: return@Await false
                            }
                            trainer?.isA(unit) == true || (trainer as? Egg)?.buildType == unit
                        }
                )
        )
    }

    fun ensureUnitDependencies(dependencies: List<UnitType>): Node {
        return DispatchParallel("ensureDependencies", {
            val missing = PlayerUnit.getMissingUnits(UnitQuery.myUnits, dependencies).toMutableList()
            if (dependencies.any { it != UnitType.Zerg_Larva && it.gasPrice() > 0 && it.gasPrice() > Board.resources.gas })
                missing.add(0, FTTConfig.GAS_BUILDING)
            missing -= Board.pendingUnits
            missing -= UnitType.Zerg_Larva
            missing -= UnitType.None
            missing.removeIf { type -> UnitQuery.myUnits.any { it.isA(type) } }
            missing

        })
        { produce(it) }
    }

    fun ensureResearchDependencies(tech: TechType): Node {
        if (tech == TechType.None) return Success
        return msequence("resDep",
                ensureDependencies(tech.requiredUnit()),
                research(tech)
        )
    }

    fun ensureDependencies(unit: UnitType): Node {
        if (unit == UnitType.None) return Success
        return parallel(2,
                Delegate { ensureUnitDependencies(unit.requiredUnits()) },
                Delegate { ensureResearchDependencies(unit.requiredTech()) }
        )
    }

    fun ensureSupply(supplyDemand: Int): Node {
        return fallback(
                ReserveResources(supply = supplyDemand),
                sequence(Condition("enough supply pending") { pendingSupply() >= 0 }, Sleep),
                sequence(Delegate { produceSupply() }, Sleep)
        )
    }

    fun research(tech: TechType): Node {
        if (tech == TechType.None) return Success
        var researcher: PlayerUnit? = null
        return fallback(
                sequence(Condition("Currently researching?") { FTTBot.self.isResearching(tech) }, Sleep),
                Condition("already researched/ing $tech") { FTTBot.self.hasResearched(tech) },
                sequence(
                        ensureUnitDependencies(listOf(tech.requiredUnit())),
                        sequence(
                                fallback(ReserveResources(tech.mineralPrice(), tech.gasPrice()), Sleep),
                                Await("can research $tech") { FTTBot.self.canResearch(tech) }
                        ),
                        Inline("find researcher for $tech") {
                            researcher = selectResearcher(researcher, tech)
                                    ?: return@Inline NodeStatus.RUNNING
                            NodeStatus.SUCCEEDED
                        },
                        Delegate { ReserveUnit(researcher!!) },
                        Delegate { ResearchCommand(researcher as ResearchingFacility, tech) }
                )
        )
    }

    fun upgrade(upgradeType: UpgradeType): Node {
        if (upgradeType == UpgradeType.None) return Success
        var researcher: PlayerUnit? = null
        val level = FTTBot.self.getUpgradeLevel(upgradeType)
        return fallback(
                sequence(Condition("Currently upgrading?") { FTTBot.self.isUpgrading(upgradeType) }, Sleep),
                Condition("already upgraded $upgradeType") { FTTBot.self.getUpgradeLevel(upgradeType) > level || level >= upgradeType.maxRepeats() },
                sequence(
                        ensureUnitDependencies(listOf(upgradeType.whatsRequired(level), upgradeType.whatUpgrades())),
                        fallback(ReserveResources(upgradeType.mineralPrice(level), upgradeType.gasPrice(level)), Sleep),
                        Await("can upgrade $upgradeType") { FTTBot.self.canUpgrade(upgradeType) },
                        Inline("find researcher for $upgradeType") {
                            researcher = selectResearcher(researcher, upgradeType)
                                    ?: return@Inline NodeStatus.RUNNING
                            NodeStatus.SUCCEEDED
                        },
                        Delegate { ReserveUnit(researcher!!) },
                        Delegate {
                            UpgradeCommand(researcher as ResearchingFacility, upgradeType)
                        }
                )
        )
    }

    fun produce(unit: UnitType, near: () -> TilePosition? = { null }): Node =
            if (unit.isBuilding && !unit.isAddon)
                build(unit, false, near)
            else
                train(unit, near)


    fun produceSupply(near: () -> TilePosition? = { null }): Node = produce(FTTConfig.SUPPLY, near)

    fun trainWorker(near: () -> TilePosition? = { bestPositionForNewWorker() }): Node = train(FTTConfig.WORKER, near)

    fun cancel(type: UnitType): Node {
        require(type.isBuilding)
        var target: Building? = null
        return sequence(
                Inline("find $type to cancel") {
                    target = UnitQuery.myUnits.filterIsInstance(Building::class.java)
                            .firstOrNull { it.isA(type) && !it.isCompleted }
                            ?: return@Inline NodeStatus.FAILED
                    NodeStatus.SUCCEEDED
                },
                Delegate { CancelCommand(target!!) }
        )
    }

    fun cancelGas() = cancel(FTTConfig.GAS_BUILDING)

    fun bestPositionForNewWorker(): TilePosition? {
        val tilePosition = (MyInfo.myBases.filter { it.isReadyForResources }.minBy {
            it as PlayerUnit
            it.getUnitsInRadius(300, UnitQuery.myWorkers).size
        } as? PlayerUnit)?.tilePosition
        return tilePosition
    }

    fun buildGas(near: () -> TilePosition? = { null }): Node = build(FTTConfig.GAS_BUILDING, at = near)
}

object BoSearch : BaseNode() {
    override fun tick(): NodeStatus {
        TODO("not implemented") //To change body flow created functions use File | Settings | File Templates.
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