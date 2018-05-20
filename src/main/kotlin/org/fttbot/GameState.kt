package org.fttbot

import org.fttbot.info.UnitQuery
import org.fttbot.task.BoSearch
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import kotlin.math.max
import kotlin.math.min

const val WORKER_MOVE_TIME = 96

data class GameState(var frame: Int,
                     var race: Race,
                     var supplyUsed: Int,
                     var supplyTotal: Int,
                     var minerals: Int,
                     var gas: Int,
                     var units: MutableMap<UnitType, MutableList<UnitState>>,
                     var tech: MutableMap<TechType, Int>, // Tech -> frame available at
                     var upgrades: MutableMap<UpgradeType, UpgradeState>) {
    val pendingSupply get() = min(400 - supplyUsed, units[race.supplyProvider]?.sumBy { it.supplyProvided } ?: 0)
    val freeSupply get() = max(0, supplyTotal - supplyUsed)
    val hasRefinery get() = units[race.refinery]?.isEmpty() == false
    val refineries get() = amountOf(race.refinery)
    val workers get() = units[race.worker] ?: mutableListOf()
    val finishedAt get() = units.values.flatten().map { it.availableAt }.max() ?: frame

    init {
        units = units.map { (k, v) -> k to v.map { it.copy() }.toMutableList() }.toMap().toMutableMap()
        tech = HashMap(tech)
        upgrades = upgrades.map { (k, v) -> k to v.copy() }.toMap().toMutableMap()
    }


    override fun toString(): String =
            "frame: $frame, minerals: $minerals, gas: $gas, supply: $supplyUsed/$supplyTotal\n" +
                    units.map { (ut, unitStates) -> unitStates.map { Pair(ut, it) } }
                            .flatten().sortedBy { it.second.startedAt }
                            .map { (ut, s) -> "$ut : $s" }.joinToString("\n")

    fun isValid(unit: UnitType): Boolean =
            units.containsKey(unit.whatBuilds().first)
                    && units.keys.containsAll(unit.requiredUnits())
                    && tech.contains(unit.requiredTech())
                    && unit.supplyRequired() <= freeSupply + pendingSupply
                    && (unit.gasPrice() <= gas || hasRefinery)
                    && (!unit.isAddon || units[unit.whatBuilds().first]!!.any { it.addon == UnitType.None })

    fun isValid(tech: TechType): Boolean =
            units.containsKey(tech.requiredUnit())
                    && units.containsKey(tech.whatResearches())
                    && (tech.gasPrice() <= gas || hasRefinery)

    fun getUpgradeLevel(upgrade: UpgradeType) = upgrades[upgrade]?.let { if (it.availableAt <= frame) it.level else it.level - 1 } ?: 0

    fun isValid(upgrade: UpgradeType): Boolean {
        val level = upgrades[upgrade]?.level ?: 0
        return level < upgrade.maxRepeats()
                && (upgrade.whatsRequired(level) == UnitType.None || units.containsKey(upgrade.whatsRequired(level)))
                && units.containsKey(upgrade.whatUpgrades())
                && (upgrade.gasPrice(level) <= gas || hasRefinery)
    }

    fun amountOf(unit: UnitType) = units[unit]?.size ?: 0
    fun amountOfBases() = amountOf(race.center) + amountOf(UnitType.Zerg_Lair) + amountOf(UnitType.Zerg_Hive)
    fun completedUnits(type: UnitType) = unitsOfType(type).filter { it.availableAt <= frame }
    fun unitsOfType(type: UnitType) = units[type]?.toList() ?: emptyList()

    fun unfinishedUnits(type: UnitType) = unitsOfType(type).filter { it.availableAt > frame }

    fun upgrade(upgrade: UpgradeType) {
        val upgradeState = upgrades.computeIfAbsent(upgrade) { UpgradeState() }
        val mineralPrice = upgrade.mineralPrice(upgradeState.level)
        val gasPrice = upgrade.gasPrice(upgradeState.level)
        acquireResources(mineralPrice, gasPrice)
        val researcher = units[upgrade.whatUpgrades()]?.minBy { it.availableAt } ?: throw IllegalStateException()
        passTimeWithDefaultWorkerLayout(max(researcher.availableAt, upgradeState.availableAt))
        val availableAt = frame + upgrade.upgradeTime(upgradeState.level)
        researcher.availableAt = availableAt
        upgradeState.availableAt = availableAt;
        upgradeState.level++
        minerals -= mineralPrice
        gas -= gasPrice
    }

    fun build(unit: UnitType) {
        if (!unit.isBuilding) throw IllegalStateException("$unit is not a building!")
        acquireResources(unit.mineralPrice(), unit.gasPrice())

        val availableAt: Int
        if (unit.isAddon) {
            val building = units[unit.whatBuilds().first]!!.minBy { it.availableAt } ?: throw IllegalStateException()
            passTimeWithDefaultWorkerLayout(building.availableAt)
            availableAt = frame + unit.buildTime()
            building.availableAt = availableAt
            building.addon = unit
        } else {
            val worker = workers.minBy { it.availableAt } ?: throw IllegalStateException()
            passTimeWithDefaultWorkerLayout(worker.availableAt)
            availableAt = frame + unit.buildTime() + WORKER_MOVE_TIME
            if (race == Race.Terran) {
                worker.availableAt = availableAt
            } else if (race == Race.Zerg) {
                workers -= worker
            }
        }
        payForAndStartUnit(unit, availableAt)
    }

    fun train(unit: UnitType) {
        if (unit.isBuilding) throw IllegalStateException("$unit is not a unit!")
        acquireResources(unit.mineralPrice(), unit.gasPrice())

        while (unit.supplyRequired() > freeSupply && pendingSupply > 0) {
            val nextSupplyFrame = units[race.supplyProvider]?.filter { it.supplyProvided > 0 }?.map { it.availableAt }?.min() ?: throw IllegalStateException()
            passTimeWithDefaultWorkerLayout(nextSupplyFrame)
        }
        if (unit.supplyRequired() > freeSupply) {
            throw IllegalStateException()
        }
        val trainers = units[unit.whatBuilds().first] ?: throw IllegalStateException()
        val requiredAddon = unit.requiredUnits().firstOrNull() { it.isAddon }
        val filteredTrainers = if (requiredAddon != null) trainers.filter { it.addon == requiredAddon } else trainers
        val trainer = filteredTrainers.minBy { it.availableAt }!!
        passTimeWithDefaultWorkerLayout(trainer.availableAt)
        val availableAt = frame + unit.buildTime()
        payForAndStartUnit(unit, availableAt)
        supplyUsed += unit.supplyRequired()
        trainer.availableAt = availableAt
    }

    private fun payForAndStartUnit(unit: UnitType, availableAt: Int) {
        minerals -= unit.mineralPrice()
        gas -= unit.gasPrice()
        units.computeIfAbsent(unit) { ArrayList() }.add(UnitState(frame, availableAt, unit.supplyProvided()))
    }

    private fun acquireResources(mineralPrice: Int, gasPrice: Int) {
        var missingMinerals = max(mineralPrice - minerals, 0)
        var missingGas = max(gasPrice - gas, 0)

        if ((missingGas > 0 || missingMinerals > 0) && workers.isEmpty() || missingGas > 0 && !hasRefinery) {
            throw IllegalStateException()
        }

        val maxGasGatherers = refineries * 3

        // Overall frames required for gathering (distributed over workforce)
        var miningFrames = missingMinerals * 1440 / 50
        var gasFrames = missingGas * 1440 / 90

        while (miningFrames > 0 || gasFrames > 0) {
            val gatheringWorkers = workers.filter { it.availableAt <= frame }
            val amountOfGatherers = gatheringWorkers.size
            val nextFreeWorker = workers.filter { it.availableAt > frame }.map { it.availableAt }.min() ?: Int.MAX_VALUE
            val forGas = min(amountOfGatherers, min(maxGasGatherers, gasFrames * amountOfGatherers / (miningFrames + gasFrames)))
            val forMinerals = amountOfGatherers - forGas
            val gatherTime = if (forMinerals == 0 && forGas > 0) gasFrames / forGas else
                if (forGas == 0 && forMinerals > 0) miningFrames / forMinerals
                else if (forGas != 0 && forMinerals != 0)
                    min(miningFrames / forMinerals, gasFrames / forGas)
                else Int.MAX_VALUE
            val passTime = min(gatherTime, nextFreeWorker - frame)

            passTime(passTime, forMinerals, forGas)
            missingMinerals = max(mineralPrice - minerals, 0)
            missingGas = max(gasPrice - gas, 0)
            miningFrames = missingMinerals * 1440 / 50
            gasFrames = missingGas * 1440 / 90
        }
    }

    private fun passTimeWithDefaultWorkerLayout(targetFrame: Int) {
        if (targetFrame <= frame) return
        while (frame < targetFrame) {
            val amountOfGatherers = workers.count { it.availableAt <= frame }
            val delta = min(targetFrame - frame, workers.filter { it.availableAt > frame }.map { it.availableAt }.min() ?: Int.MAX_VALUE)
            val maxGasGatherers = refineries * 3
            val gasGatherers = min(maxGasGatherers, amountOfGatherers / 4)
            val mineralGatherers = amountOfGatherers - gasGatherers
            passTime(delta, mineralGatherers, gasGatherers)
        }
    }

    fun finish() {
        val unitsFinishedAt = units.values.flatten().map { it.availableAt }.max() ?: frame
        val techFinishedAt = tech.values.max() ?: frame
        val upgradeFinishedAt = upgrades.values.map { it.availableAt }.max() ?: frame
        passTimeWithDefaultWorkerLayout(max(max(unitsFinishedAt, techFinishedAt), upgradeFinishedAt))
    }

    private fun passTime(deltaTime: Int, mineralWorkers: Int, gasWorkers: Int) {
        val actualDelta =
                max(deltaTime, max(
                        if (mineralWorkers > 0) (1440 + (mineralWorkers * 50 - 1)) / mineralWorkers / 50 else 0,
                        if (gasWorkers > 0) (1440 + (gasWorkers * 90 - 1)) / gasWorkers / 90 else 0))
        frame += actualDelta
        minerals += mineralWorkers * 50 * actualDelta / 1440
        gas += gasWorkers * 90 * actualDelta / 1440
        units[race.supplyProvider]?.filter { it.supplyProvided > 0 && it.availableAt <= frame }?.forEach { supplyTotal = min(supplyTotal + it.supplyProvided, 400); it.supplyProvided = 0 }
    }

    data class UnitState(val startedAt: Int = -1, var availableAt: Int = 0, var supplyProvided: Int = 0, var addon: UnitType = UnitType.None)

    data class UpgradeState(val startedAt: Int = -1, var availableAt: Int = 0, var level: Int = 0)

    companion object {
        fun fromCurrent() : GameState {
            val myRace = FTTConfig.MY_RACE
            val researchFacilities = UnitQuery.myUnits.filterIsInstance(ResearchingFacility::class.java)
            val upgradesInProgress = researchFacilities.map {
                val upgrade = it.upgradeInProgress
                upgrade.upgradeType to GameState.UpgradeState(-1, upgrade.remainingUpgradeTime, FTTBot.self.getUpgradeLevel(upgrade.upgradeType) + 1)
            }.toMap()
            val upgrades = UpgradeType.values()
                    .filter { it.race == myRace }
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
            val supplyUsed = UnitQuery.myUnits.filterIsInstance(MobileUnit::class.java).sumBy { it.supplyRequired }
            val supplyTotal = UnitQuery.myUnits.filterIsInstance(SupplyProvider::class.java).sumBy { it.supplyProvided() }
            val researchInProgress = researchFacilities.map {
                val research = it.researchInProgress
                research.researchType to research.remainingResearchTime
            }
            val tech = (TechType.values()
                    .filter { it.race == myRace && FTTBot.self.hasResearched(it) }
                    .map { it to 0 } + Pair(TechType.None, 0) + researchInProgress).toMap().toMutableMap()

            return GameState(0, FTTBot.self.race, supplyUsed, supplyTotal, FTTBot.self.minerals(), FTTBot.self.gas(),
                    units, tech, upgrades)
        }
    }
}