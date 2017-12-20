package org.fttbot.layer

import bwapi.*
import bwapi.Unit
import org.fttbot.FTTBot
import org.fttbot.translated


class FUnit private constructor(val unit: Unit) {
    companion object {
        private val units = HashMap<Int, FUnit>()

        fun of(unit: Unit): FUnit = units.computeIfAbsent(unit.id) { FUnit(unit) }
        fun destroy(unit: Unit) = units.remove(unit.id)

        fun allUnits() = FTTBot.game.allUnits.map { FUnit.of(it) }
        fun unitsInRadius(position: Position, radius: Int) = FTTBot.game.getUnitsInRadius(position,radius ).map { FUnit.of(it) }
        fun minerals() = FTTBot.game.minerals.map { FUnit.of(it) }
        fun neutrals() = FTTBot.game.neutralUnits.map { FUnit.of(it) }
        fun myUnits() = FTTBot.game.allUnits.filter { it.player == FTTBot.self }.map { FUnit.of(it) }
        fun myBases() = myUnits().filter { it.isBase }
        fun myWorkers() = FTTBot.game.allUnits.filter { it.type.isWorker && it.player == FTTBot.self}.map { FUnit.of(it) }
    }

    fun closest(others: Iterable<FUnit>) : FUnit? {
        return others.minBy { it.distanceTo(this) }
    }

    val isWorker: Boolean get() = type.isWorker
    val isGatheringMinerals: Boolean get() = unit.isGatheringMinerals
    val isConstructing get() = unit.isConstructing
    val buildUnit get() = unit.buildUnit
    val buildType get() = FUnitType.of(unit.buildType)
    val isIdle get() = unit.isIdle
    val position: Position get() = unit.position
    val isMineralField: Boolean get() = unit.type.isMineralField
    val isRefinery get() = unit.type.isRefinery
    val isPlayerOwned: Boolean get() = unit.player == FTTBot.self
    val type: FUnitType get() = FUnitType.of(unit.type)
    val isCarryingGas get() = unit.isCarryingGas
    val isCarryingMinerals get() = unit.isCarryingMinerals
    val isAttackFrame get() = unit.isAttackFrame
    val isBase
        get() = when (unit.type) {
            UnitType.Terran_Command_Center -> true
            UnitType.Protoss_Nexus -> true
            UnitType.Zerg_Hatchery -> true
            UnitType.Zerg_Lair -> true
            UnitType.Zerg_Hive -> true
            else -> false
        }
    val isTraining: Boolean get() = unit.buildUnit != null || !unit.trainingQueue.isEmpty()
    val isBuilding get() = unit.type.isBuilding
    val isMoving: Boolean get() = unit.isMoving
    val initialTilePosition get() = unit.initialTilePosition
    val tilePosition get() = unit.tilePosition
    val targetPosition get() = unit.targetPosition

    fun lastCommand() = unit.lastCommand

    fun distanceTo(other: FUnit) = position.getDistance(other.position)
    fun distanceTo(position: TilePosition) = position.getDistance(tilePosition)

    fun gather(target: FUnit) = unit.gather(target.unit)
    fun returnCargo() = if (unit.lastCommand.unitCommandType != UnitCommandType.Return_Cargo) unit.returnCargo() else true
    fun attack(targetUnit: FUnit) = if (unit.lastCommand.unitCommandType != UnitCommandType.Attack_Unit || unit.lastCommand.target != targetUnit.unit) unit.attack(targetUnit.unit) else true
    fun construct(type: FUnitType, position: TilePosition) = unit.build(type.type, position)
    fun move(position: TilePosition) = unit.move(position.toPosition())
    fun train(type: FUnitType) = unit.train(type.type)

    override fun toString(): String = "${unit.type.toString().substringAfter('_')} at ${unit.position}"
}
