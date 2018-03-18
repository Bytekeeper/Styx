package org.fttbot

import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit

open class Order<E : kotlin.Any>(val unit: E, val order: E.() -> Boolean, val toleranceFrames: Int = 30) : Node {
    var orderFrame: Int = -1

    override fun tick(): NodeStatus {
        if (orderFrame < 0) {
            orderFrame = FTTBot.frameCount
        }
        if (FTTBot.frameCount - orderFrame > toleranceFrames) {
            orderFrame = -1
            return NodeStatus.FAILED
        }
        if (order(unit)) {
            orderFrame = -1
            return NodeStatus.SUCCEEDED
        }
        return NodeStatus.RUNNING
    }

}

class MoveCommand(unit: MobileUnit, position: Position) : Order<MobileUnit>(unit, { move(position) })
class BuildCommand(worker: Worker, position: TilePosition, building: UnitType) : Order<Worker>(worker, {
    require(building.gasPrice() <= FTTBot.self.gas())
    require(building.mineralPrice() <= FTTBot.self.minerals())
    build(position, building)
})

class CancelCommand(building: Building) : Order<Building>(building, { cancelConstruction() })
class TrainCommand(trainer: TrainingFacility, unit: UnitType) : Order<TrainingFacility>(trainer, { train(unit) })
class MorphCommand(morphable: Morphable, unit: UnitType) : Order<Morphable>(morphable, {
    require(unit.gasPrice() <= FTTBot.self.gas())
    require(unit.mineralPrice() <= FTTBot.self.minerals())
    require(unit.supplyRequired() == 0 || unit.supplyRequired() <= FTTBot.self.supplyTotal() - FTTBot.self.supplyUsed())
    morph(unit)
})

class ResearchCommand(researcher: ResearchingFacility, tech: TechType) : Order<ResearchingFacility>(researcher,
        {
            require(tech.gasPrice() <= FTTBot.self.gas())
            require(tech.mineralPrice() <= FTTBot.self.minerals())
            research(tech)
        })

class UpgradeCommand(researcher: ResearchingFacility, upgrade: UpgradeType) : Order<ResearchingFacility>(researcher, { upgrade(upgrade) })
class Attack(unit: PlayerUnit, target: Unit) : Order<PlayerUnit>(unit, { (this as Attacker).attack(target) })
class GatherMinerals(worker: Worker, mineralPatch: MineralPatch) : Order<Worker>(worker, { gather(mineralPatch) })
class GatherGas(worker: Worker, gasPatch: GasMiningFacility) : Order<Worker>(worker, { gather(gasPatch) })
class Repair(worker: SCV, target: Mechanical) : Order<SCV>(worker, { repair(target) })
class Heal(medic: Medic, target: Organic) : Order<Medic>(medic, { healing(target as PlayerUnit) })

class ArriveAt(val unit: MobileUnit, val position: Position, val tolerance: Int = 32) : Node {
    override fun tick(): NodeStatus {
        if (unit.getDistance(position) <= tolerance)
            return NodeStatus.SUCCEEDED
        return NodeStatus.RUNNING
    }
}

class ReserveUnit(val unit: PlayerUnit) : Node {
    override fun tick(): NodeStatus {
        if (Board.resources.units.contains(unit)) {
            Board.resources.reserveUnit(unit)
            return NodeStatus.SUCCEEDED
        }
        return NodeStatus.FAILED
    }
}

class ReserveResources(val minerals: Int, val gas: Int = 0) : Node {
    override fun tick(): NodeStatus {
        Board.resources.reserve(minerals, gas)
        if (Board.resources.enoughMineralsAndGas()) return NodeStatus.SUCCEEDED
        return NodeStatus.FAILED
    }
}

class ReserveSupply(val supply: Int) : Node {
    override fun tick(): NodeStatus =
            if (supply == 0 || Board.resources.reserve(supply = supply).supply >= 0)
                NodeStatus.SUCCEEDED
            else NodeStatus.FAILED
}

class BlockResources(val minerals: Int, val gas: Int = 0, val supply: Int = 0) : Node {
    override fun tick(): NodeStatus {
        if (Board.resources.isAvailable(minerals, gas, supply)) return NodeStatus.SUCCEEDED
        Board.resources.reserve(minerals, gas, supply)
        return NodeStatus.RUNNING
    }
}

object CompoundActions {
    fun reach(unit: MobileUnit, position: Position, tolerance: Int): Node {
        // TODO: "Search" for a way
        return MSequence("reach", MoveCommand(unit, position), ArriveAt(unit, position, tolerance))
    }

}