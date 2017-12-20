package org.fttbot.behavior

import bwapi.TilePosition
import org.fttbot.layer.FUnit
import org.fttbot.layer.FUnitType
import java.util.*

open class BBUnit(val unit: FUnit,
                  var order: Order = Order.NONE) {
    class Attacking(val target: FUnit) : Order()

    companion object {
        private val boards = HashMap<FUnit, BBUnit>()

        fun of(unit: FUnit) = boards.computeIfAbsent(unit) { BBUnit(unit) }
    }
}

open class Order {
    companion object {
        val NONE = Order()
    }
}

class Construction(val type: FUnitType, var position: TilePosition? = null, var started : Boolean = false) : Order()
class Gathering(var target: FUnit? = null) : Order()
class Training(val type: FUnitType) : Order()

object Production {
    val queue = ArrayDeque<Item>()
    var reservedMinerals = 0
    var reservedGas = 0
    var queueNeedsRebuild = true

    class Item(val type: FUnitType)
}