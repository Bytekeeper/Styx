package org.fttbot

import org.apache.logging.log4j.LogManager
import org.fttbot.info.UnitQuery
import org.fttbot.task.Production.morph
import org.fttbot.task.Production.research
import org.fttbot.task.Production.train
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit

open class Order<E : kotlin.Any>(val unit: E, val order: E.() -> Boolean, val toleranceFrames: Int = 30) : BaseNode() {
    var orderFrame: Int = -1

    override fun tick(): NodeStatus {
        if (orderFrame < 0) {
            orderFrame = FTTBot.frameCount
        }
        if (FTTBot.frameCount - orderFrame > toleranceFrames) {
            return NodeStatus.FAILED
        }
        if (order(unit)) {
            return NodeStatus.SUCCEEDED
        }
        return NodeStatus.RUNNING
    }

    override fun parentFinished() {
        orderFrame = -1
        super.parentFinished()
    }
}

class MoveCommand(unit: MobileUnit, position: Position) : Order<MobileUnit>(unit, { move(position) })
class BuildCommand(worker: Worker, position: TilePosition, building: UnitType) : Order<Worker>(worker, {
    require(building.gasPrice() <= FTTBot.self.gas())
    require(building.mineralPrice() <= FTTBot.self.minerals())
    if (building.supplyProvided() == 0 && UnitQuery.myUnits.any { it.isA(building) } && !building.isRefinery && !building.isResourceDepot) {
        LogManager.getLogger().warn("Building another {}", building)
    }
    if (Board.resources.minerals < 0) {
        LogManager.getLogger().warn("Why would you even do that?")
    }
    build(position, building)
})

class CancelCommand(building: Building) : Order<Building>(building, { cancelConstruction() })
class TrainCommand(trainer: TrainingFacility, unit: UnitType) : Order<TrainingFacility>(trainer, { train(unit) })
class MorphCommand(morphable: Morphable, unit: UnitType) : Order<Morphable>(morphable, {
    require(unit.gasPrice() <= FTTBot.self.gas())
    require(unit.mineralPrice() <= FTTBot.self.minerals())
    require(unit.supplyRequired() == 0 || unit.supplyRequired() <= FTTBot.self.supplyTotal() - FTTBot.self.supplyUsed())
    if (Board.resources.minerals < 0) {
        LogManager.getLogger().warn("Why would you even do that?")
    }
    morph(unit)
})

class ResearchCommand(researcher: ResearchingFacility, tech: TechType) : Order<ResearchingFacility>(researcher,
        {
            require(tech.gasPrice() <= FTTBot.self.gas())
            require(tech.mineralPrice() <= FTTBot.self.minerals())
            research(tech)
        })

class UpgradeCommand(researcher: ResearchingFacility, upgrade: UpgradeType) : Order<ResearchingFacility>(researcher, {
    upgrade(upgrade)
})
class AttackCommand(unit: PlayerUnit, target: Unit) : Order<PlayerUnit>(unit, { (this as Attacker).attack(target) })
class BurrowCommand(unit: PlayerUnit) : Order<PlayerUnit>(unit, { (this as Burrowable).burrow() })
class UnburrowCommand(unit: PlayerUnit) : Order<PlayerUnit>(unit, { (this as Burrowable).unburrow() })
class GatherMinerals(worker: Worker, mineralPatch: MineralPatch) : Order<Worker>(worker, { gather(mineralPatch) })
class GatherGas(worker: Worker, gasPatch: GasMiningFacility) : Order<Worker>(worker, { gather(gasPatch) })
class Repair(worker: SCV, target: Mechanical) : Order<SCV>(worker, { repair(target) })


class ReserveUnit(val unit: PlayerUnit) : BaseNode() {
    override fun tick(): NodeStatus {
        if (!unit.exists()) {
            return NodeStatus.FAILED
        }
        if (Board.resources.units.contains(unit)) {
            Board.resources.reserveUnit(unit)
            return NodeStatus.SUCCEEDED
        }
        LogManager.getLogger().error("Failed to reserve $unit: ${getTreeTrace().joinToString("\n")}");
        return NodeStatus.FAILED
    }

    override fun toString(): String = "Reserving unit $unit"
}

class ReserveResources(val minerals: Int = 0, val gas: Int = 0, val supply: Int = 0) : BaseNode() {
    override fun tick(): NodeStatus {
        val availableResources = Board.resources
        availableResources.reserve(minerals, gas, supply)
        if (minerals > 0 && availableResources.minerals < 0) return NodeStatus.FAILED
        if (gas > 0 && availableResources.gas < 0) return NodeStatus.FAILED
        if (supply > 0 && availableResources.supply < 0) return NodeStatus.FAILED
        return NodeStatus.SUCCEEDED
    }
}

class ReleaseResources(val minerals: Int = 0, val gas: Int = 0, val supply: Int = 0) : BaseNode() {
    override fun tick(): NodeStatus {
        val availableResources = Board.resources
        availableResources.release(minerals, gas, supply)
        return NodeStatus.SUCCEEDED
    }
}
