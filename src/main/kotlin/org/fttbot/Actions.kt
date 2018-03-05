package org.fttbot

import org.fttbot.Board.resources
import org.fttbot.info.UnitQuery
import org.fttbot.info.findWorker
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit

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
class ResearchCommand(researcher: ResearchingFacility, tech: TechType) : Order<ResearchingFacility>(researcher, { research(tech) })
class Attack(unit: PlayerUnit, target: Unit) : Order<PlayerUnit>(unit, { (this as Armed).attack(target) })
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
    fun buildWithWorker(worker: Worker, at: TilePosition, building: UnitType): Node {
        return AtLeastOne(
                ConstructionStarted(worker, building, at),
                Sequence(
                        ReserveUnit(worker),
                        Fallback(ReserveResources(building.mineralPrice(), building.gasPrice()), Sleep),
                        MSequence("executeBuild",
                                reach(worker, at.toPosition(), 128),
                                Build(worker, at, building),
                                Await(48) { worker.isConstructing },
                                Sleep)
                ))
    }

    private fun reach(unit: MobileUnit, position: Position, tolerance: Int): Node {
        // TODO: "Search" for a way
        return MSequence("reach", Move(unit, position), ArriveAt(unit, position, tolerance))
    }

    fun selectTrainer(currentTrainer: PlayerUnit?, unit: UnitType, near: Position?): PlayerUnit? {
        if (currentTrainer?.exists() == true && (currentTrainer !is TrainingFacility || !currentTrainer.isTraining)) return currentTrainer
        return Board.resources.units
                .filter {
                    it.isA(unit.whatBuilds().first)
                            && (it !is TrainingFacility || !it.isTraining)
                }
                .minBy { near?.getDistance(it.position) ?: 0 }
    }

    fun selectResearcher(currentResearcher: PlayerUnit?, tech: TechType): PlayerUnit? {
        if (currentResearcher?.exists() == true && !(currentResearcher as ResearchingFacility).isResearching
                && !(currentResearcher as ResearchingFacility).isUpgrading) return currentResearcher
        return Board.resources.units
                .firstOrNull {
                    it.isA(tech.whatResearches())
                            && it is ResearchingFacility
                            && !it.isResearching
                            && !it.isUpgrading
                }
    }

    fun build(type: UnitType, at: () -> TilePosition? = { null }): Node {
        require(type.isBuilding && !type.isAddon)
        var targetPosition: TilePosition? = null
        var builder: PlayerUnit? = null
        return Retry(3, MSequence("build",
                ensureDependencies(type),
                Sequence(
                        BlockResources(type.mineralPrice(), type.gasPrice()),
                        Await { FTTBot.self.canMake(type) },
                        Inline {
                            targetPosition = at() ?: ConstructionPosition.findPositionFor(type)
                                    ?: return@Inline NodeStatus.FAILED
                            builder = if (type.whatBuilds().first.isWorker)
                                findWorker(targetPosition!!.toPosition(), candidates = resources.units.filterIsInstance(Worker::class.java))
                            else
                                Board.resources.units.firstOrNull { it.isA(type.whatBuilds().first) && it is Building && it.remainingBuildTime == 0 }
                                        ?: return@Inline NodeStatus.FAILED
                            NodeStatus.SUCCEEDED
                        }
                ),
                MDelegate
                {
                    if (builder is Worker)
                        buildWithWorker(builder as Worker, targetPosition!!, type)
                    else
                        morph(builder as Morphable, type)
                }
        ))
    }

    fun morph(unit: Morphable, to: UnitType): Node {
        return MSequence("morph",
                Sequence(
                        ReserveUnit(unit as PlayerUnit),
                        Fallback(ReserveResources(to.mineralPrice(), to.gasPrice()), Sleep)
                ),
                MDelegate {
                    MorphCommand(unit, to)
                }
        )
    }

    fun train(unit: UnitType, near: () -> TilePosition? = { null }): Node {
        require(!unit.isBuilding || unit.isAddon)
        var trainer: PlayerUnit? = null
        val supplyUsed = if (unit == UnitType.Zerg_Zergling) 2 * unit.supplyRequired() else unit.supplyRequired()
        return MSequence("train",
                ensureDependencies(unit),
                Sequence(
                        All(
                                ensureSupply(supplyUsed),
                                Fallback(ReserveResources(unit.mineralPrice(), unit.gasPrice()), Sleep)
                        ),
                        Await { FTTBot.self.canMake(unit) },
                        Inline {
                            trainer = selectTrainer(trainer, unit, near()?.toPosition())
                                    ?: return@Inline NodeStatus.RUNNING
                            NodeStatus.SUCCEEDED
                        },
                        Delegate { ReserveUnit(trainer!!) },
                        MDelegate {
                            when (trainer) {
                                is Morphable -> MorphCommand(trainer as Morphable, unit)
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

    fun ensureUnitDependencies(dependencies: List<UnitType>): Node {
        return Synced({
            val missing = PlayerUnit.getMissingUnits(UnitQuery.myUnits, dependencies)
            if (dependencies.any { it.gasPrice() > 0 }) missing += FTTConfig.GAS_BUILDING
            missing.removeIf { type -> UnitQuery.myUnits.any { it.isA(type) } }
            missing

        }, "ensureDependencies")
        { produce(it) }
    }

    fun ensureResearchDependencies(tech: TechType): Node {
        if (tech == TechType.None) return Success
        return MSequence("resDep",
                ensureDependencies(tech.requiredUnit()),
                research(tech)
        )
    }

    fun ensureDependencies(unit: UnitType): Node {
        if (unit == UnitType.None) return Success
        return All(
                MDelegate { ensureUnitDependencies(unit.requiredUnits()) },
                ensureResearchDependencies(unit.requiredTech())
        )
    }

    fun ensureSupply(supplyDemand: Int): Fallback {
        return Fallback(
                ReserveSupply(supplyDemand),
                Require {
                    UnitQuery.myUnits.any {
                        !it.isCompleted && it is SupplyProvider
                                || it is Egg && it.buildType.supplyProvided() > 0
                    }
                },
                MDelegate { buildOrTrainSupply() }
        )
    }

    fun research(tech: TechType): Node {
        var researcher: PlayerUnit? = null
        return MSequence("research",
                ensureUnitDependencies(listOf(tech.requiredUnit())),
                Sequence(
                        Fallback(ReserveResources(tech.mineralPrice(), tech.gasPrice()), Sleep),
                        Await { FTTBot.self.canResearch(tech) }
                ),
                Inline {
                    researcher = selectResearcher(researcher, tech)
                            ?: return@Inline NodeStatus.RUNNING
                    NodeStatus.SUCCEEDED
                },
                Delegate { ReserveUnit(researcher!!) },
                MDelegate {
                    ResearchCommand(researcher as ResearchingFacility, tech)
                }
        )
    }

    fun produce(unit: UnitType, near: () -> TilePosition? = { null }): Node =
            if (unit.isBuilding && !unit.isAddon)
                build(unit, near)
            else
                train(unit, near)


    fun buildOrTrainSupply(near: () -> TilePosition? = { null }): Node = produce(FTTConfig.SUPPLY)

    fun trainWorker(near: () -> TilePosition? = { null }): Node = train(FTTConfig.WORKER, near)
}