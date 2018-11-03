package org.fttbot

import org.fttbot.FTTBot.self
import org.fttbot.info.MyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.task.Action
import org.fttbot.task.TaskStatus
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Egg
import org.openbw.bwapi4j.unit.PlayerUnit

object ProductionBoard {
    val pendingLocations = ArrayList<TilePosition>()
    val pendingUnits = ArrayList<UnitType>()

    fun startedUnits() = UnitQuery.myUnits
            .filter { !it.isCompleted }
            .map { if (it is Egg) it.buildType else it.type }

    fun reset() {
        pendingUnits.clear()
        pendingLocations.clear()
    }
}

object ResourcesBoard {
    private val _units = mutableSetOf<PlayerUnit>()
    val units: Set<PlayerUnit> get() = _units
    val completedUnits get() = units.filter { it.isCompleted }
    var minerals: Int = 0
        private set
    var gas: Int = 0
        private set
    var supply: Int = 0
        private set

    fun canAfford(minerals: Int, gas: Int, supply: Int = 0, futureFrames: Int = 0) =
            (minerals == 0 || this.minerals + MyInfo.mineralsPerFrame * futureFrames >= minerals) &&
                    (gas == 0 || this.gas + MyInfo.gasPerFrame * futureFrames >= gas) &&
                    (supply == 0 || this.supply >= supply)

    fun canAfford(unitType: UnitType, futureFrames: Int = 0) = canAfford(unitType.mineralPrice(), unitType.gasPrice(), unitType.supplyRequired(), futureFrames)

    fun reserveUnits(toReserve: Collection<PlayerUnit>): ResourcesBoard {
        require(_units.containsAll(toReserve))
        _units.removeAll(toReserve)
        return this
    }

    fun reserve(minerals: Int = 0, gas: Int = 0, supply: Int = 0): ResourcesBoard {
        this.minerals -= minerals
        this.gas -= gas
        this.supply -= supply
        return this
    }

    fun release(unit: PlayerUnit) {
        require(!_units.contains(unit))
        require(unit.exists())
        _units += unit
    }

    fun reserveUnit(unit: PlayerUnit): ResourcesBoard {
        require(_units.contains(unit))
        _units.remove(unit)
        return this
    }

    fun reset() {
        this.minerals = self.minerals()
        this.gas = self.gas()
        this.supply = self.supplyTotal() - self.supplyUsed()
        this._units.clear()
        this._units.addAll(UnitQuery.myUnits)
    }

    fun acquire(minerals: Int = 0, gas: Int = 0, supply: Int = 0, futureFrames: Int = 0) =
            if (!canAfford(minerals, gas, supply, futureFrames)) {
                reserve(minerals, gas, supply)
                false
            } else {
                reserve(minerals, gas, supply)
                true
            }

    fun acquireFor(unitType: UnitType) = acquire(unitType.mineralPrice(), unitType.gasPrice(), unitType.supplyRequired())
    fun acquireFor(type: TechType) = acquire(type.mineralPrice(), type.gasPrice())
}


class ReserveUnit(private val unit: PlayerUnit, utility: Double = 1.0) : Action(utility) {
    override fun processInternal(): TaskStatus {
        if (ResourcesBoard.units.contains(unit)) {
            ResourcesBoard.reserveUnit(unit)
            return TaskStatus.DONE
        }
        return TaskStatus.FAILED
    }
}

class ReleaseUnit(private val unit: PlayerUnit, utility: Double = 1.0) : Action(utility) {
    override fun processInternal(): TaskStatus {
        ResourcesBoard.release(unit)
        return TaskStatus.DONE
    }
}