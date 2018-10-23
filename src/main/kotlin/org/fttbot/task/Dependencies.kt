package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.ProductionBoard
import org.fttbot.ResourcesBoard
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.GasMiningFacilityImpl
import org.openbw.bwapi4j.unit.Hatchery

class EnsureUnitDependencies(val type: UnitType) : Action() {
    private val unitsToBuild by SubTask {
        ParallelTask(ManagedTaskProvider<UnitType>({
            type.requiredUnits().keys
        }) { if (it.isBuilding) HaveBuilding(it) else HaveUnit(it) })
    }

    private val techToResearch by SubTask {
        if (type.requiredTech() != TechType.None) Research(type.requiredTech()) { 1.0 }
        else Success(1.0)
    }

    private val supplyToHave by SubTask {
        HaveSupply(type.supplyRequired())
    }

    override fun processInternal(): TaskStatus {
        return processAll(unitsToBuild, techToResearch, supplyToHave)
    }

}

class HaveBuilding(val type: UnitType, val utilityProvider: () -> Double = { 1.0 }) : Task() {
    override val utility: Double
        get() = utilityProvider()
    private val constructBuilding by SubTask { Build(type) }

    override fun toString() = "Have $type"

    override fun processInternal(): TaskStatus {
        val candidates = UnitQuery.myUnits.filter {
            when (type) {
                UnitType.Zerg_Hatchery -> it.type == UnitType.Zerg_Hatchery || it.type == UnitType.Zerg_Lair || it.type == UnitType.Zerg_Hive
                UnitType.Zerg_Lair -> it.type == UnitType.Zerg_Lair || it.type == UnitType.Zerg_Hive
                else -> it.type == type
            }
        }
        if (candidates.any { it.isCompleted }) return TaskStatus.DONE
        if (candidates.isNotEmpty() || ProductionBoard.pendingUnits.any { it == type }) return TaskStatus.RUNNING

        return constructBuilding.process()
    }
}

class HaveUnit(val type: UnitType) : Action() {
    private val train by SubTask { Train(type) }
    private val buildHatch by SubTask { HaveBuilding(UnitType.Zerg_Hatchery) }

    override fun toString() = "Have $type"

    override fun processInternal(): TaskStatus {
        val candidates = UnitQuery.myUnits.filter { it.type == type }
        if (candidates.any { it.isCompleted }) return TaskStatus.DONE
        if (candidates.isNotEmpty() || ProductionBoard.pendingUnits.any { it == type }) return TaskStatus.RUNNING
        if (type == UnitType.Zerg_Larva) {
            return if (UnitQuery.myUnits.any { it is Hatchery }) TaskStatus.RUNNING
            else buildHatch.process()
        }
        return train.process()
    }
}

class HaveGas(var amount: Int) : Action() {
    private val constructGasMine: Build by SubTask { Build(FTTBot.self.race.refinery) }
    override fun processInternal(): TaskStatus {
        if (ResourcesBoard.gas >= amount || amount <= 0) return TaskStatus.DONE
        if (UnitQuery.my<GasMiningFacilityImpl>().any()) return TaskStatus.RUNNING
        return constructGasMine.process()
    }
}

class HaveSupply(val supplyRequired: Int) : Action() {
    private val trainOverlord by SubTask {
        Train(UnitType.Zerg_Overlord)
    }

    override fun processInternal(): TaskStatus {
        if (supplyRequired == 0 || supplyRequired <= ResourcesBoard.supply) return TaskStatus.DONE
        val pendingInProduction = (ProductionBoard.startedUnits().count { it == UnitType.Zerg_Overlord } +
                ProductionBoard.pendingUnits.count { it == UnitType.Zerg_Overlord }) * UnitType.Zerg_Overlord.supplyProvided() +
                ResourcesBoard.supply
        if (pendingInProduction >= supplyRequired) return TaskStatus.RUNNING
        return trainOverlord.process()
    }
}

class EnsureTechDependencies(val type: TechType) : Action() {
    private val researcherToHave by SubTask {
        HaveBuilding(type.whatResearches())
    }

    private val gasToHave: Task by SubTask { HaveGas(type.gasPrice()) }

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
    private val upgradeToResearch: Task by SubTask { EnsureUpgradeDependencies(type, level - 1) }
    private val researcherToHave: Task by SubTask {
        HaveBuilding(type.whatUpgrades())
    }
    private val dependencyToHave: Task by SubTask {
        HaveBuilding(type.whatsRequired(level - 1))
    }
    private val gasToHave: Task by SubTask { HaveGas(type.gasPrice(level - 1)) }

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
