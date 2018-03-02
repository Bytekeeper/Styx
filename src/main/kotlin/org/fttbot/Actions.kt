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
        if (FTTBot.frameCount - orderFrame > toleranceFrames) return NodeStatus.FAILED
        if (order(unit)) {
            return NodeStatus.SUCCEEDED
        }
        return NodeStatus.RUNNING
    }

}

class Move(unit: MobileUnit, position: Position) : Order<MobileUnit>(unit, { move(position) })
class Build(worker: Worker, position: TilePosition, building: UnitType) : Order<Worker>(worker, { build(position, building) })
class TrainCommand(trainer: TrainingFacility, unit: UnitType) : Order<TrainingFacility>(trainer, { train(unit) })
class MorphCommand(larva: Larva, unit: UnitType) : Order<Larva>(larva, { morph(unit) })
class Attack(unit: PlayerUnit, target: Unit) : Order<PlayerUnit>(unit, { (this as Armed).attack(target) })
class GatherMinerals(worker: Worker, mineralPatch: MineralPatch) : Order<Worker>(worker, { gather(mineralPatch) })
class GatherGas(worker: Worker, gasPatch: GasMiningFacility) : Order<Worker>(worker, { gather(gasPatch) })
class Repair(worker: SCV, target: Mechanical) : Order<SCV>(worker, { repair(target) })
class Heal(medic: Medic, target: Organic) : Order<Medic>(medic, { healing(target as PlayerUnit) })

class ArriveAt(val unit: MobileUnit, val position: Position, val threshold: Int = 32) : Node {
    override fun tick(): NodeStatus {
        if (unit.getDistance(position) < threshold)
            return NodeStatus.SUCCEEDED
        return NodeStatus.RUNNING
    }
}

class ConstructionFinished(val worker: Worker) : Node {
    var started = false

    override fun tick(): NodeStatus {
        if (started && worker.buildType == UnitType.None) {
            return NodeStatus.SUCCEEDED
        }
        if (worker.buildType != UnitType.None) {
            started = true;
        }
        return NodeStatus.FAILED
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
        Board.resources.reserveMinerals(minerals)
                .reserveGas(gas)
                .reserveSupply(supply)
        if (Board.resources.enough()) return NodeStatus.SUCCEEDED
        return NodeStatus.RUNNING
    }
}

object CompoundActions {
    fun build(worker: Worker, at: Position, building: UnitType): Node {
        return Fallback(
                ConstructionFinished(worker),
                Sequence(
                        ReserveResources(building.mineralPrice(), building.gasPrice()),
                        ReserveUnit(worker),
                        MSequence(
                                reach(worker, at),
                                Build(worker, at.toTilePosition(), building),
                                Sleep)
                ))
    }

    private fun reach(unit: MobileUnit, position: Position): Node {
        // TODO: "Search" for a way
        return MSequence(Move(unit, position), ArriveAt(unit, position))
    }

    fun selectTrainer(currentTrainer: PlayerUnit?, unit: UnitType, near: Position?): PlayerUnit? {
        if (currentTrainer?.exists() == true) return currentTrainer
        return Board.resources.units.firstOrNull { it.isA(unit.whatBuilds().first) }
    }

    fun build(type: UnitType, at: Position? = null): Node {
        var targetPosition = at?.toTilePosition()
        var builder: Worker? = null
        return Sequence(
                Select {
                    targetPosition = targetPosition ?: ConstructionPosition.findPositionFor(type) ?: return@Select NodeStatus.FAILED
                    builder = findWorker(targetPosition!!.toPosition(), candidates = resources.units.filterIsInstance(Worker::class.java)) ?: return@Select NodeStatus.FAILED
                    NodeStatus.SUCCEEDED
                },
                Then {
                    build(builder!!, targetPosition!!.toPosition(), type)
                }
        )
    }

    fun train(unit: UnitType, near: Position? = null): Node {
        var trainer: PlayerUnit? = null
        return MSequence(
                Sequence(
                        ReserveResources(unit.mineralPrice(), unit.gasPrice(), unit.supplyRequired()),
                        Select {
                            if (FTTBot.self.canMake(unit))
                                NodeStatus.SUCCEEDED
                            else
                                NodeStatus.RUNNING
                        },
                        Select {
                            trainer = selectTrainer(trainer, unit, near) ?: return@Select NodeStatus.FAILED
                            NodeStatus.SUCCEEDED
                        },
                        Then {
                            ReserveUnit(trainer as PlayerUnit)
                        },
                        Then {
                            when (trainer) {
                                is Larva -> MorphCommand(trainer as Larva, unit)
                                else -> TrainCommand(trainer as TrainingFacility, unit)
                            }
                        }
                ),
                Select {
                    if (!trainer!!.exists()) {
                        trainer = UnitQuery.myUnits.firstOrNull { it.id == trainer!!.id } ?: return@Select NodeStatus.FAILED
                    }
                    if (trainer?.isA(unit) == true)
                        return@Select NodeStatus.SUCCEEDED
                    NodeStatus.RUNNING
                }
        )
    }
}