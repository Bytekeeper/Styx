package org.fttbot.task

import org.fttbot.*
import org.fttbot.FTTBot.frameCount
import org.fttbot.Info.totalSupplyWithPending
import org.fttbot.ProductionQueue.hasEnqueued
import org.fttbot.ProductionQueue.nextItem
import org.fttbot.behavior.*
import org.fttbot.info.*
import org.fttbot.search.MCTS
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.max

object ProductionQueueTask : Task {
    private val LOG = Logger.getLogger(this::class.java.simpleName)
    private var reservedMinerals = 0
    private var reservedGas = 0
    private var reservedSupply = 0
    override val utility: Double
        get() = 0.15

    override fun run(available: Resources): TaskResult {
        val reserved = ProductionQueue.reservedResourcesForPending
        reservedMinerals = reserved.minerals
        reservedGas = reserved.gas
        reservedSupply = reserved.supply

        val workers = available.units.filterIsInstance(Worker::class.java).toMutableList()
        val assignedProducers = UnitQuery.myUnits.filter {
            it.userData != null &&
                    (it.board.goal is Construction || it.board.goal is Research || it.board.goal is Upgrade || it.board.goal is BuildAddon)
        }.toMutableList()
        workers -= assignedProducers.filterIsInstance(Worker::class.java)

        val constructionWorkers = workers.filter { it.board.goal is Construction }
        val assignedWorkersForMissingConstructions = ProductionQueue.pendingWithMissingBuilder.mapNotNull {
            val pending = it
            LOG.warning("Worker missing for $it")
            ProductionQueue.cancel(pending)
            val worker = construct(workers, BOUnit(it.type))?.first ?: return@mapNotNull null
            ProductionQueue.setInProgress(BOUnit(pending.type), worker)
            return@mapNotNull worker
        }

        while (ProductionQueue.hasItems) {
            val nextItem = ProductionQueue.nextItem
            if (!canAfford(available, nextItem)) {
                if (nextItem.supplyRequired > max(0, available.supply)) {
                    ProductionQueue.prepend(BOUnit(FTTBot.self.race.supplyProvider))
                } else
                    break
            } else if (canAfford(available, nextItem)) {
                if (!nextItem.canProcess) {
                    if (checkCancelUnproccessable(nextItem)) {
                        ProductionQueue.cancelNextItem()
                    }
                    break
                } else {
                    val builderType = nextItem.whatProduces
                    if (builderType.isWorker) {
                        val (worker, construction) = construct(available.units, nextItem) ?: Pair(null, null)
                        if (construction != null && worker != null) {
                            assignedProducers.add(worker)
                            workers -= worker
                            ProductionQueue.setInProgress(nextItem as BOUnit, worker)
                            reserve(nextItem)
                        } else {
                            LOG.severe("Failed to produce $nextItem, will skip this one")
                            ProductionQueue.cancelNextItem()
                        }
                    } else {
                        val potentialProducers = available.units.filter { it.isA(nextItem.whatProduces) && it.board.goal == null }
                        val producer = when (nextItem) {
                            is BOUnit -> if (nextItem.type.isAddon) buildAddon(potentialProducers, nextItem) else train(potentialProducers, nextItem)
                            is BOResearch -> research(potentialProducers, nextItem)
                            is BOUpgrade -> upgrade(potentialProducers, nextItem)
                            else -> {
                                LOG.severe("Failed to produce $nextItem")
                                null
                            }
                        }
                        if (producer != null) {
                            ProductionQueue.setInProgress(nextItem, producer)
                            reserve(nextItem)
                        } else
                            break
                    }
                }
            }
        }

        return TaskResult(Resources(constructionWorkers + assignedWorkersForMissingConstructions + assignedProducers))
    }

    private fun checkCancelUnproccessable(item: BOItem): Boolean {
        if (item is BOUpgrade && FTTBot.self.getUpgradeLevel(item.type) >= item.type.maxRepeats()) {
            LOG.severe("Requested too many upgrades for $item, cancelling")
            return true
        }
        if (item is BOResearch && (FTTBot.self.isResearching(item.type) || FTTBot.self.hasResearched(item.type))) {
            LOG.severe("Already researched/researching $item, cancelling")
            return true
        }
        if (item is BOUnit && item.type.isAddon &&
                UnitQuery.myUnits.none { it.isA(item.type.whatBuilds().first) && it is ExtendibleByAddon && it.addon == null }
                && !hasEnqueued(item.type.whatBuilds().first)) {
            LOG.severe("Requested addon, but no free ${item.type.whatBuilds().first} is available for ${item.type}")
            return true;
        }
        return false
    }

    private fun canAfford(available: Resources, item: BOItem) = item.gasPrice <= available.gas - reservedGas &&
            item.mineralPrice <= available.minerals - reservedMinerals && item.supplyRequired <= max(0, available.supply - reservedSupply)

    private fun reserve(item: BOItem) {
        reservedSupply += item.supplyRequired
        reservedMinerals += item.mineralPrice
        reservedGas += item.gasPrice
    }

    private fun train(availableUnits: Collection<PlayerUnit>, train: BOUnit): PlayerUnit? {
        val trainer = availableUnits.minBy { it.getDistance(train.position ?: it.position) } ?: return null
        trainer.board.goal = Train(train.type)
        return trainer
    }

    private fun upgrade(availableUnits: Collection<PlayerUnit>, upgrade: BOUpgrade): PlayerUnit? {
        val researcher = availableUnits.minBy { it.getDistance(upgrade.position ?: it.position) } ?: return null
        researcher.board.goal = Upgrade(upgrade.type)
        return researcher
    }

    private fun research(availableUnits: Collection<PlayerUnit>, research: BOResearch): PlayerUnit? {
        val researcher = availableUnits.minBy { it.getDistance(research.position ?: it.position) } ?: return null
        researcher.board.goal = Research(research.type)
        return researcher
    }

    private fun buildAddon(availableUnits: Collection<PlayerUnit>, addon: BOUnit): PlayerUnit? {
        if (!addon.type.isAddon) throw IllegalStateException("${addon.type} is not an addon!")
        val builder = availableUnits.minBy {
            it.getDistance(addon.position ?: it.position)
        } ?: return null
        builder.board.goal = BuildAddon(addon.type)
        return builder
    }

    private fun construct(availableUnits: Collection<PlayerUnit>, item: BOItem): Pair<Worker<*>, Construction>? {
        if (item !is BOUnit) return null
        val position = ConstructionPosition.findPositionFor(item.type, item.position) ?: return null
        val worker = findWorker(position.toPosition(), candidates = availableUnits.filterIsInstance(Worker::class.java))
                ?: return null
        val construction = Construction(item.type, position)
        worker.board.goal = construction
        return Pair(worker, construction)
    }

}

object ResumeConstructionsTask : Task {
    private val LOG = Logger.getLogger(this::class.java.simpleName)
    override val utility: Double
        get() = 0.1

    override fun run(available: Resources): TaskResult {
        val workers = available.units.filterIsInstance(SCV::class.java)
        val buildingsWithoutWorker = UnitQuery.myUnits.filterIsInstance(Building::class.java).filter { !it.isCompleted && !it.isBeingConstructed } -
                workers.mapNotNull {
                    val goal = it.board.goal as? ResumeConstruction ?: return@mapNotNull null
                    goal.building
                }
        val usedWorkers = buildingsWithoutWorker.mapNotNull { buildingToComplete ->
            val worker = findWorker(buildingToComplete.position, candidates = workers) ?: return@mapNotNull null
            worker.board.goal = ResumeConstruction(buildingToComplete)
            worker
        }
        return TaskResult(Resources(usedWorkers))
    }
}

object SupplyBuilderTask : Task {
    override val utility: Double = BuildUtility.buildSupply()

    override fun run(available: Resources): TaskResult {
        if (ProductionQueue.hasItems || BuildUtility.buildSupply() < 0.7) return TaskResult.RUNNING
        val supplyProvider = FTTConfig.SUPPLY
        if (!ProductionQueue.hasEnqueued(supplyProvider)) {
            ProductionQueue.enqueue(BOUnit(supplyProvider))
        }
        return TaskResult.RUNNING
    }
}


object HandleSupplyBlock : Task {
    override val utility: Double
        get() {
            val nextItem = nextItem ?: return 0.0
            if (nextItem.supplyRequired <= 0) return 0.0
            if (nextItem.supplyRequired <= totalSupplyWithPending - FTTBot.self.supplyUsed()) return 0.0
            return 1.0
        }

    override fun run(available: Resources): TaskResult {
        if (nextItem == null || nextItem.supplyRequired <= totalSupplyWithPending - FTTBot.self.supplyUsed()) return TaskResult()
        // At maximum supply, no further units should be queued
        if (totalSupplyWithPending >= 400) {
            ProductionQueue.cancelNextItem()
        } else if (available.minerals >= FTTConfig.SUPPLY.mineralPrice()){
            ProductionQueue.prepend(BOUnit(FTTBot.self.race.supplyProvider))
        }
        return TaskResult.RUNNING
    }
}

object DetectorProduction : Task {
    override val utility: Double
        get() = StrategyUtility.needMobileDetection()

    override fun run(available: Resources): TaskResult {
        if (ProductionQueue.hasItems) return TaskResult.RUNNING
        val candidates = UnitType.values()
                .filter { it.race == FTTBot.self.race && it.mineralPrice() > 0 && it.isDetector || it == UnitType.Terran_Comsat_Station }
        if (UnitQuery.myUnits.any { ut -> candidates.any { ut.isA(it) } }) return TaskResult.RUNNING
        val alreadyGot = candidates.filter { ProductionQueue.hasEnqueued(it) }
        val remaining = UnitType.Terran_Comsat_Station.allRequiredUnits() - alreadyGot
        if (!remaining.isEmpty()) {
            val toBuild = PlayerUnit.getMissingUnits(UnitQuery.myUnits, remaining)
                    .firstOrNull { ut ->
                        FTTBot.self.canMake(ut)
                    } ?: return TaskResult.RUNNING
            ProductionQueue.enqueue(BOUnit(toBuild))
        }
        return TaskResult.RUNNING
    }
}


object DoUpgrades : Task {
    override val utility: Double
        get() = 0.1

    override fun run(available: Resources): TaskResult {
        if (ProductionQueue.hasItems) return TaskResult.RUNNING
        val myUnits = UnitQuery.myUnits
        val upgradesToConsider = UpgradeType.values().filter { ut -> FTTBot.self.canUpgrade(ut) && !FTTBot.self.isUpgrading(ut) }
                .sortedByDescending { ut -> myUnits.count { unit -> ut.whatUses().any { unit.isA(it) } } }
        val toUpgrade = upgradesToConsider.firstOrNull() ?: return TaskResult.RUNNING
        if (UnitQuery.myUnits.any { it.isA(toUpgrade.whatUpgrades()) && it.board.goal == null }) {
            ProductionQueue.enqueue(BOUpgrade(toUpgrade))
        }
        return TaskResult.RUNNING
    }
}


object ProduceAttacker : Task {
    override val utility: Double
        get() = 0.2

    override fun run(available: Resources): TaskResult {
        if (ProductionQueue.hasItems) return TaskResult.RUNNING
        val availableTrainers = UnitQuery.myUnits.filterIsInstance(TrainingFacility::class.java)
                .filter { !it.isTraining && it.trainingQueueSize == 0 }
        val toTrain = availableTrainers.mapNotNull { trainer ->
            trainer.trains().firstOrNull {
                it.canAttack() && !it.isWorker && it.canMove()
                        && FTTBot.self.canMake(trainer as Unit, it)
            }
        }.firstOrNull() ?: return TaskResult.RUNNING
        if (available.minerals >= toTrain.mineralPrice() && available.gas >= toTrain.gasPrice() && available.supply >= toTrain.supplyRequired() )
        ProductionQueue.enqueue(BOUnit(toTrain))
        return TaskResult(Resources(minerals = toTrain.mineralPrice(), gas = toTrain.gasPrice(), supply = toTrain.supplyRequired() ))
    }
}


object BoSearch : Task {
    private val LOG = Logger.getLogger(this::class.java.simpleName)
    private var mcts: MCTS? = null
    override val utility: Double = 0.8
    private var relocateTo: MCTS.Node? = null

    override fun run(available: Resources): TaskResult {
        if (mcts == null) {
            mcts = MCTS(mapOf(UnitType.Terran_Marine to 2, UnitType.Terran_Factory to 2), setOf(), mapOf(UpgradeType.Ion_Thrusters to 1), Race.Terran)
        }
        if (ProductionQueue.hasItems) return TaskResult.RUNNING

        val searcher = mcts ?: throw IllegalStateException()
        if (PlayerUnit.getMissingUnits(UnitQuery.myUnits, UnitType.Terran_Marine.requiredUnits() + UnitType.Terran_Vulture.requiredUnits())
                        .isEmpty() && FTTBot.self.getUpgradeLevel(UpgradeType.Ion_Thrusters) > 0) return TaskResult(status = TaskStatus.SUCCESSFUL)
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
            return TaskResult.RUNNING
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
            repeat(100) {
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
                        if (!ProductionQueue.hasEnqueued(move.unit)) if (!move.unit.isRefinery || !state.hasRefinery) ProductionQueue.enqueue(BOUnit(move.unit))
                        return TaskResult(Resources(minerals = move.unit.mineralPrice(), gas = move.unit.gasPrice(), supply = move.unit.supplyRequired()))
                    }
                    is MCTS.UpgradeMove -> {
                        if (!ProductionQueue.hasEnqueued(move.upgrade)) ProductionQueue.enqueue(BOUpgrade(move.upgrade))
                        val level = FTTBot.self.getUpgradeLevel(move.upgrade)
                        return TaskResult(Resources(minerals = move.upgrade.mineralPrice(level), gas = move.upgrade.gasPrice(level)))
                    }
                }
            }
            return TaskResult.RUNNING
        } catch (e: IllegalStateException) {
            LOG.log(Level.SEVERE, "Couldn't determine build order, guess it's over", e)
        }
        return TaskResult(status = TaskStatus.FAILED)
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

object WorkerProduction : Task {
    override val utility: Double
        get() = 0.1

    override fun run(available: Resources): TaskResult {
        if (ProductionQueue.hasItems) return TaskResult.RUNNING
        UnitQuery.myBases.forEach { base ->
            val units = UnitQuery.unitsInRadius(base.position, 300)
            val workerDelta = units.map { if (it is MineralPatch) 2 else if (it is GasMiningFacility) 3 else if (it is MobileUnit && it is Worker<*> && it.isMyUnit) -1 else 0 }.sum()
            if (workerDelta > 0 && available.minerals >= FTTConfig.WORKER.mineralPrice() && available.supply >= FTTConfig.WORKER.supplyRequired()) {
                ProductionQueue.enqueue(BOUnit(FTTConfig.WORKER, base.position))
                return TaskResult(Resources(minerals = FTTConfig.WORKER.mineralPrice(), supply = FTTConfig.WORKER.supplyRequired()))
            }
        }
        return TaskResult.RUNNING
    }
}

object TrainUnits : Task {
    private val LOG = Logger.getLogger(this::class.java.simpleName)
    override val utility: Double = 0.1

    override fun run(available: Resources): TaskResult {
        val used = available.units.filterIsInstance(TrainingFacility::class.java)
                .mapNotNull { trainer ->
                    if (trainer !is PlayerUnit) return@mapNotNull null
                    val goal = trainer.board.goal as? Train ?: return@mapNotNull null
                    if (trainer.isTraining ||
                            trainer.trainingQueueSize > 0 ||
                            !trainer.canTrain(goal.type)) return@mapNotNull trainer as PlayerUnit
                    if (!trainer.train(goal.type)) {
                        LOG.severe("Couldn't start training ${goal.type}")
                        return@mapNotNull trainer as PlayerUnit
                    }
                    LOG.info("${trainer} started training ${goal.type}")
                    trainer.board.goal = null
                    return@mapNotNull trainer as PlayerUnit
                }
        return TaskResult(Resources(used))
    }
}


object BuildAddons : Task {
    private val LOG = Logger.getLogger(this::class.java.simpleName)
    override val utility: Double = 0.1

    override fun run(available: Resources): TaskResult {
        val used = available.units.filterIsInstance(ExtendibleByAddon::class.java)
                .mapNotNull { extendible ->
                    if (extendible !is PlayerUnit || extendible !is TrainingFacility) return@mapNotNull null
                    val goal = extendible.board.goal as? BuildAddon ?: return@mapNotNull null
                    if (extendible.isTraining || extendible.trainingQueueSize > 0) return@mapNotNull extendible as PlayerUnit
                    if (extendible.addon != null) {
                        LOG.severe("Couldn't build ${goal.type}, already got addon ${extendible.addon}")
                        extendible.board.goal = null
                        return@mapNotNull null
                    }
                    if (!extendible.canTrain(goal.type)) return@mapNotNull extendible as PlayerUnit
                    if (!extendible.build(goal.type)) {
                        LOG.severe("Couldn't build ${goal.type}")
                    }
                    extendible.board.goal = null
                    return@mapNotNull extendible as PlayerUnit
                }
        return TaskResult(Resources(used))
    }

}