package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.ProductionBoard
import org.fttbot.ResourcesBoard
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.GasMiningFacility
import org.openbw.bwapi4j.unit.GasMiningFacilityImpl

class EnsureUnitDependencies(val type: UnitType) : Action() {
    private val buildingsToConstruct by SubTask {
        ParallelTask(ManagedTaskProvider<UnitType>({
            type.requiredUnits().keys
                    .filter { it.isBuilding && UnitQuery.myUnits.none { u -> u.type == it } } -
                    ProductionBoard.pendingUnits - type.whatBuilds().unitType
        }) { ConstructBuilding(it) })
    }

    private val techToResearch by SubTask {
        if (type.requiredTech() != TechType.None) Research(type.requiredTech()) { 1.0 }
        else Success(1.0)
    }

    override fun processInternal(): TaskStatus {
        return processAll(buildingsToConstruct, techToResearch)
    }

}

class HaveBuilding(val type: UnitType) : Action() {
    private val constructBuilding: ConstructBuilding by subtask { ConstructBuilding(type) }
    override fun processInternal(): TaskStatus {
        val candidates = UnitQuery.myUnits.filter { it.type == type }
        if (candidates.any { it.isCompleted }) return TaskStatus.DONE
        if (candidates.isNotEmpty() || ProductionBoard.pendingUnits.any { it == type }) return TaskStatus.RUNNING

        return constructBuilding.process()
    }
}

class HaveGas(var amount: Int) : Action() {
    private val constructGasMine: ConstructBuilding by subtask { ConstructBuilding(FTTBot.self.race.refinery) }
    override fun processInternal(): TaskStatus {
        if (ResourcesBoard.gas >= amount || amount <= 0) return TaskStatus.DONE
        if (UnitQuery.my<GasMiningFacilityImpl>().any()) return TaskStatus.RUNNING
        return constructGasMine.process()
    }
}

class EnsureTechDependencies(val type: TechType) : Action() {
    private val researcherToHave by SubTask {
        HaveBuilding(type.whatResearches())
    }

    private val gasToHave: Task by subtask { HaveGas(type.gasPrice()) }

    private val dependencyToHave by SubTask {
        HaveBuilding(type.requiredUnit())
    }

    override fun processInternal(): TaskStatus {
        val requiredUnit = type.requiredUnit()
        if (requiredUnit == UnitType.None) return processAll(researcherToHave, gasToHave)
        return processAll(researcherToHave, dependencyToHave, gasToHave)
    }
}


class EnsureUpgradeDependencies(val type: UpgradeType, val level: Int) : Action() {
    private val upgradeToResearch: Task by subtask { EnsureUpgradeDependencies(type, level - 1) }
    private val researcherToHave: Task by subtask {
        HaveBuilding(type.whatUpgrades())
    }
    private val dependencyToHave: Task by subtask {
        HaveBuilding(type.whatsRequired(level - 1))
    }
    private val gasToHave: Task by subtask { HaveGas(type.gasPrice(level - 1)) }

    init {
        require(level <= type.maxRepeats())
    }

    override fun processInternal(): TaskStatus {
        val currentLevel = FTTBot.self.getUpgradeLevel(type)
        if (currentLevel >= level) return TaskStatus.DONE
        if (currentLevel < level - 1) {
            return upgradeToResearch.process()
        }
        if (type.whatsRequired(level - 1) == UnitType.None)
            return processAll(researcherToHave, gasToHave)
        return processAll(researcherToHave, dependencyToHave, gasToHave)
    }

}
