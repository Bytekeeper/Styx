package org.fttbot

import org.fttbot.Board.resources
import org.fttbot.info.UnitQuery
import org.fttbot.info.findWorker
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit

interface PlanAction {
    fun cost(state: World): Int
}

open class Order<E : Any>(val unit: E, val order: E.() -> Boolean, val toleranceFrames: Int = 10) : Node {
    var orderFrame: Int = -1

    override fun tick(): NodeStatus {
        if (orderFrame < 0) {
            orderFrame = FTTBot.frameCount
        }
        if (FTTBot.frameCount - orderFrame > toleranceFrames)
            return NodeStatus.FAILED
        if (order(unit)) {
            return NodeStatus.SUCCEEDED
        }
        return NodeStatus.RUNNING
    }

}

class Move(unit: MobileUnit, position: Position) : Order<MobileUnit>(unit, { move(position) })
class Build(worker: Worker, position: TilePosition, building: UnitType) : Order<Worker>(worker, { build(position, building) })
class TrainCommand(trainer: TrainingFacility, unit: UnitType) : Order<TrainingFacility>(trainer, { train(unit) })
class MorphCommand(morphable: Morphable, unit: UnitType) : Order<Morphable>(morphable, { morph(unit) })
class Attack(unit: PlayerUnit, target: Unit) : Order<PlayerUnit>(unit, { (this as Armed).attack(target) })
class GatherMinerals(worker: Worker, mineralPatch: MineralPatch) : Order<Worker>(worker, { gather(mineralPatch) })
class GatherGas(worker: Worker, gasPatch: GasMiningFacility) : Order<Worker>(worker, { gather(gasPatch) })
class Repair(worker: SCV, target: Mechanical) : Order<SCV>(worker, { repair(target) })
class Heal(medic: Medic, target: Organic) : Order<Medic>(medic, { healing(target as PlayerUnit) })

class ArriveAt(val unit: MobileUnit, val position: Position, val tolerance: Int = 32) : Node {
    override fun tick(): NodeStatus {
        if (unit.getDistance(position) < tolerance)
            return NodeStatus.SUCCEEDED
        return NodeStatus.RUNNING
    }
}

class ConstructionStarted(val worker: Worker, val type: UnitType, val at: TilePosition) : Node {
    var started = false

    override fun tick(): NodeStatus {
        if (started) {
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

class ReserveUnit(val unit: PlayerUnit) : Node {
    override fun tick(): NodeStatus {
        if (Board.resources.units.contains(unit)) {
            Board.resources.reserveUnit(unit)
            return NodeStatus.SUCCEEDED
        }
        return NodeStatus.FAILED
    }
}

class ReserveResources(val minerals: Int, val gas: Int = 0, val supply: Int = 0) : Node {
    override fun tick(): NodeStatus {
        Board.resources.reserve(minerals, gas, supply)
        if (Board.resources.enough()) return NodeStatus.SUCCEEDED
        return NodeStatus.RUNNING
    }
}

class BlockResources(val minerals: Int, val gas: Int = 0, val supply: Int = 0) : Node {
    override fun tick(): NodeStatus {
        if (Board.resources.isAvailable(minerals, gas, supply)) return NodeStatus.SUCCEEDED
        Board.resources.reserve(minerals, gas, supply)
        return NodeStatus.RUNNING
    }
}

object CompoundActions {
    fun build(worker: Worker, at: TilePosition, building: UnitType): Node {
        return AtLeastOne(
                ConstructionStarted(worker, building, at),
                Sequence(
                        ReserveUnit(worker),
                        ReserveResources(building.mineralPrice(), building.gasPrice()),
                        MSequence(
                                reach(worker, at.toPosition(), 96),
                                Build(worker, at, building),
                                Sleep)
                ))
    }

    private fun reach(unit: MobileUnit, position: Position, tolerance: Int): Node {
        // TODO: "Search" for a way
        return MSequence(Move(unit, position), ArriveAt(unit, position, tolerance))
    }

    fun selectTrainer(currentTrainer: PlayerUnit?, unit: UnitType, near: Position?): PlayerUnit? {
        if (currentTrainer?.exists() == true) return currentTrainer
        return Board.resources.units
                .filter {
                    it.isA(unit.whatBuilds().first)
                            && (it !is TrainingFacility || !it.isTraining)
                }
                .minBy { near?.getDistance(it.position) ?: 0 }
    }

    fun build(type: UnitType, at: Position? = null): Node {
        var targetPosition = at?.toTilePosition()
        var builder: Worker? = null
        return Retry(3, MSequence(
                Sequence(
                        BlockResources(type.mineralPrice(), type.gasPrice()),
                        Inline {
                            targetPosition = ConstructionPosition.findPositionFor(type) ?: return@Inline NodeStatus.FAILED
                            builder = findWorker(targetPosition!!.toPosition(), candidates = resources.units.filterIsInstance(Worker::class.java)) ?: return@Inline NodeStatus.FAILED
                            NodeStatus.SUCCEEDED
                        }
                ),
                MDelegate
                {
                    build(builder!!, targetPosition!!, type)
                }
        ))
    }

    fun train(unit: UnitType, near: Position? = null): Node {
        var trainer: PlayerUnit? = null
        val supplyUsed = if (unit == UnitType.Zerg_Zergling) 2 * unit.supplyRequired() else unit.supplyRequired()
        return MSequence(
                Sequence(
                        ReserveResources(unit.mineralPrice(), unit.gasPrice(), supplyUsed),
                        Await { FTTBot.self.canMake(unit) },
                        Inline {
                            trainer = selectTrainer(trainer, unit, near)
                                    ?: return@Inline NodeStatus.RUNNING
                            NodeStatus.SUCCEEDED
                        },
                        Delegate { ReserveUnit(trainer as PlayerUnit) },
                        MDelegate {
                            when (trainer) {
                                is Larva -> MorphCommand(trainer as Larva, unit)
                                else -> TrainCommand(trainer as TrainingFacility, unit)
                            }
                        }
                ),
                Inline {
                    if (!trainer!!.exists()) {
                        trainer = UnitQuery.myUnits.firstOrNull { it.id == trainer!!.id } ?: return@Inline NodeStatus.FAILED
                    }
                    if (trainer?.isA(unit) == true)
                        return@Inline NodeStatus.SUCCEEDED
                    NodeStatus.RUNNING
                }
        )
    }
}