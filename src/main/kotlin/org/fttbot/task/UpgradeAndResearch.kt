package org.fttbot.task

import org.fttbot.ProductionBoard
import org.fttbot.FTTBot
import org.fttbot.ResourcesBoard
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.ResearchingFacility

class Research(val type: TechType, val utilityProvider: UtilityProvider) : Task() {
    private val dependencies: Task by subtask { EnsureTechDependencies(type) }
    private val researcherLock = UnitLock<PlayerUnit> { it as ResearchingFacility; !it.isResearching && !it.isUpgrading }

    override val utility: Double
        get() = utilityProvider()

    override fun toString(): String = "Research $type"

    override fun processInternal(): TaskStatus {
        if (FTTBot.self.hasResearched(type)) return TaskStatus.DONE
        if (FTTBot.self.isResearching(type)) return TaskStatus.RUNNING

        dependencies.process().whenRunning { return TaskStatus.RUNNING }

        if (!ResourcesBoard.canAfford(type.mineralPrice(), type.gasPrice())) {
            ResourcesBoard.reserve(type.mineralPrice(), type.gasPrice())
            return TaskStatus.RUNNING
        }
        ResourcesBoard.reserve(type.mineralPrice(), type.gasPrice())

        val researcher = researcherLock.acquire {
            ResourcesBoard.units.firstOrNull { it.isA(type.whatResearches()) && it.isCompleted && !(it as ResearchingFacility).isResearching }
        } ?: return TaskStatus.RUNNING

        researcher as ResearchingFacility

        researcher.research(type)
        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {

        override fun invoke(): List<Task> = listOf()

    }
}

class Upgrade(val type: UpgradeType, val level: Int, val utilityProvider: UtilityProvider) : Task() {
    private val dependencies: Task by subtask { EnsureUpgradeDependencies(type, level) }
    private val researcherLock = UnitLock<PlayerUnit> { it as ResearchingFacility; !it.isResearching && !it.isUpgrading }

    override val utility: Double
        get() = utilityProvider()

    override fun toString(): String = "Upgrade $type"

    override fun processInternal(): TaskStatus {
        if (FTTBot.self.getUpgradeLevel(type) >= level) return TaskStatus.DONE
        if (FTTBot.self.isUpgrading(type)) return TaskStatus.RUNNING

        dependencies.process().whenRunning { return TaskStatus.RUNNING }

        if (!ResourcesBoard.canAfford(type.mineralPrice(level - 1), type.gasPrice(level - 1))) {
            ResourcesBoard.reserve(type.mineralPrice(level - 1), type.gasPrice(level - 1))
            return TaskStatus.RUNNING
        }
        ResourcesBoard.reserve(type.mineralPrice(level - 1), type.gasPrice(level - 1))

        val researcher = researcherLock.acquire {
            ResourcesBoard.units.firstOrNull { it.isA(type.whatUpgrades()) && it.isCompleted && !(it as ResearchingFacility).isUpgrading }
        } ?: return TaskStatus.RUNNING

        researcher as ResearchingFacility

        researcher.upgrade(type)
        return TaskStatus.RUNNING

    }

    companion object : TaskProvider {
        private val upgrades: List<Task> = listOf(
                Upgrade(UpgradeType.Metabolic_Boost, 1, { 0.7 }),
                Upgrade(UpgradeType.Grooved_Spines, 1, { 0.65 }),
                Upgrade(UpgradeType.Muscular_Augments, 1, { 0.65 })
        )

        override fun invoke(): List<Task> = upgrades
    }
}