package org.fttbot.behavior

import org.fttbot.FTTBot
import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit
import java.util.*

open class BBUnit(val unit: PlayerUnit) {
    var moveTarget: Position? = null
    var targetResource: Unit? = null
    var status: String = "<Nothing>"
    var construction: Construction? = null
    var scouting: Scouting? = null
    var attacking: Attacking? = null
    var goal: Goal? = null
    var combatSuccessProbability: Double = 0.5
    val utility = UtilityStats()
    val importance: Double = 1.0
}

class UtilityStats(var attack: Double = 0.0,
                   var defend: Double = 0.0,
                   var force : Double = 0.0,
                   var threat : Double = 0.0,
                   var value : Double = 0.0,
                   var construct : Double = 0.0,
                   var gather : Double = 0.0)

abstract class Goal(val name: String)

class Repair(val target: PlayerUnit) : Goal("Repair")
class BeRepairedBy(val worker: SCV) : Goal("Being repaired")

class Construction(val type: UnitType, var position: TilePosition) {
    // Confirmed by the engine to have started
    var started: Boolean = false
    // Worker called "build"
    var commissioned: Boolean = false
    var building: Building? = null
}

class Scouting(val locations: Deque<Position>) {
    var points: List<Position>? = null
    var index: Int = 0
}

class Attacking(val target: Unit)

object ProductionBoard {
    val orderedConstructions = ArrayDeque<Construction>()
    val queue = ArrayDeque<ProductionItem>()
    var reservedMinerals = 0
    var reservedGas = 0

    fun updateReserved() {
        reservedMinerals = 0
        reservedGas = 0
        orderedConstructions.filter { !it.started }
                .forEach {
                    reservedMinerals += it.type.mineralPrice()
                    reservedGas += it.type.gasPrice()
                }
    }

    abstract class ProductionItem(val favoriteProducer: Unit? = null) {
        abstract val canAfford: Boolean
        abstract val canProcess: Boolean
        abstract val whatProduces: UnitType
        protected fun canAfford(mineralPrice: Int, gasPrice: Int) = reservedMinerals + mineralPrice <= FTTBot.self.minerals() && reservedGas + gasPrice <= FTTBot.self.gas()
    }

    class UnitItem(val type: UnitType, favoriteBuilder: Unit? = null) : ProductionItem(favoriteBuilder) {
        override val canAfford: Boolean
            get() = canAfford(type.mineralPrice(), type.gasPrice())
        override val canProcess: Boolean
            get() = FTTBot.self.canMake(type) && (!type.isAddon ||
                    UnitQuery.myUnits.any { it is ExtendibleByAddon && it.addon == null && it.isA(type.whatBuilds().first) })
        override val whatProduces: UnitType = type.whatBuilds().first

        override fun toString(): String = "unit $type at $favoriteProducer"
    }

    class TechItem(val type: TechType, favoriteResearcher: Unit? = null) : ProductionItem(favoriteResearcher) {
        override val canAfford: Boolean
            get() = canAfford(type.mineralPrice(), type.gasPrice())
        override val canProcess: Boolean
            get() = FTTBot.self.canResearch(type)
        override val whatProduces: UnitType = type.whatResearches()
    }

    class UpgradeItem(val type: UpgradeType, favoriteUpgrader: Unit? = null) : ProductionItem(favoriteUpgrader) {
        override val canAfford: Boolean
            get() {
                val upgradeLevel = FTTBot.self.getUpgradeLevel(type)
                if (upgradeLevel >= type.maxRepeats()) return true
                return canAfford(type.mineralPrice(upgradeLevel), type.gasPrice(upgradeLevel))
            }

        override val canProcess: Boolean
            get() = FTTBot.self.canUpgrade(type)
        override val whatProduces: UnitType = type.whatUpgrades()
    }
}

object ScoutingBoard {
    var lastScoutFrameCount: Int = 0
}