package org.fttbot

import org.fttbot.info.UnitQuery
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.ExtendibleByAddon
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Unit

abstract class BOItem(val position: Position? = null) {
    abstract val canProcess: Boolean
    abstract val whatProduces: UnitType
    abstract val mineralPrice : Int
    abstract val gasPrice: Int
    abstract val supplyRequired : Int
}

class BOUnit(val type: UnitType, position: Position? = null) : BOItem(position) {
    override val canProcess: Boolean
        get() = FTTBot.self.canMake(type) && (!type.isAddon ||
                UnitQuery.myUnits.any { it is ExtendibleByAddon && it.addon == null && it.isA(type.whatBuilds().first) })
    override val whatProduces: UnitType = type.whatBuilds().first

    override val mineralPrice: Int = type.mineralPrice()
    override val gasPrice: Int = type.gasPrice()
    override val supplyRequired: Int = type.supplyRequired()

    override fun toString(): String = "unit $type at $position"
}

class BOResearch(val type: TechType, position: Position? = null) : BOItem(position) {
    override val mineralPrice: Int = type.mineralPrice()
    override val gasPrice: Int = type.gasPrice()
    override val supplyRequired: Int = 0
    override val canProcess: Boolean
        get() = FTTBot.self.canResearch(type)
    override val whatProduces: UnitType = type.whatResearches()
}

class BOUpgrade(val type: UpgradeType, position: Position? = null) : BOItem(position) {
    private val upgradeLevel = FTTBot.self.getUpgradeLevel(type)
    override val mineralPrice: Int = type.mineralPrice(upgradeLevel)
    override val gasPrice: Int = type.gasPrice(upgradeLevel)
    override val supplyRequired: Int = 0
    override val canProcess: Boolean
        get() = FTTBot.self.canUpgrade(type)
    override val whatProduces: UnitType = type.whatUpgrades()
}
