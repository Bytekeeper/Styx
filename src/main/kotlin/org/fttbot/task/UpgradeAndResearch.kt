package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.ResourcesBoard
import org.fttbot.UnitLocked
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.Mutalisk
import org.openbw.bwapi4j.unit.ResearchingFacility

class Research(val type: TechType, val utilityProvider: UtilityProvider = { 1.0 }) : Task() {
    private val dependencies: Task by SubTask { EnsureTechDependencies(type) }
    private val researcherLock = UnitLocked<ResearchingFacility>(this) { !it.isResearching && !it.isUpgrading }

    override val utility: Double
        get() = utilityProvider()

    override fun toString(): String = "Research $type"

    override fun processInternal(): TaskStatus {
        if (FTTBot.self.hasResearched(type)) return TaskStatus.DONE
        if (FTTBot.self.isResearching(type)) return TaskStatus.RUNNING

        dependencies.process().andWhenRunning { return TaskStatus.RUNNING }

        if (!ResourcesBoard.acquireFor(type)) {
            return TaskStatus.RUNNING
        }

        val researcher = researcherLock.compute { inv ->
            ResourcesBoard.completedUnits.filterIsInstance<ResearchingFacility>()
                    .firstOrNull { it.type == type.whatResearches() && inv(it) }
        } ?: return TaskStatus.RUNNING

        researcher.research(type)
        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {

        override fun invoke(): List<Task> = listOf()

    }
}

class Upgrade(val type: UpgradeType, private val level: Int = 0, val utilityProvider: UtilityProvider = { 1.0 }) : Task() {
    private val dependencies: Task by SubTask{ EnsureUpgradeDependencies(type, level) }
    private val researcherLock = UnitLocked<ResearchingFacility>(this) { !it.isResearching && !it.isUpgrading }

    override val utility: Double
        get() = utilityProvider()

    override fun toString(): String = "Upgrade $type"

    override fun processInternal(): TaskStatus {
        if (FTTBot.self.getUpgradeLevel(type) >= level) return TaskStatus.DONE
        if (FTTBot.self.isUpgrading(type)) return TaskStatus.RUNNING

        dependencies.process().andWhenRunning { return TaskStatus.RUNNING }

        if (!ResourcesBoard.acquire(type.mineralPrice(level - 1), type.gasPrice(level - 1))) {
            return TaskStatus.RUNNING
        }
        val researcher = researcherLock.compute { inv ->
            ResourcesBoard.completedUnits.filterIsInstance<ResearchingFacility>().firstOrNull {
                it.type == type.whatUpgrades() && inv(it)
            }
        } ?: return TaskStatus.RUNNING

        researcher.upgrade(type)
        return TaskStatus.RUNNING

    }

    companion object : TaskProvider {
        private val upgrades: List<Task> = listOf(
                Upgrade(UpgradeType.Metabolic_Boost, 1, { 0.65 }),
                Fallback(Sequence(Condition { UnitQuery.myUnits.any { it is Mutalisk } }, Upgrade(UpgradeType.Zerg_Flyer_Carapace, 1)), Running())
//                Upgrade(UpgradeType.Grooved_Spines, 1, { 0.65 }),
//                Upgrade(UpgradeType.Muscular_Augments, 1, { 0.65 })
        )

        override fun invoke(): List<Task> = upgrades
    }
}