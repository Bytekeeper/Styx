package org.fttbot.estimation

import org.openbw.bwapi4j.type.UnitType
import kotlin.math.abs

object BOPrediction {
    val predictors = HashMap<Item, List<Prediction>>()

    fun bestMatch(type: UnitType, amount: Int) =
            predictors.keys.filter { it.type == type }.minBy { abs(it.amount - amount) }


    class Item(val type: UnitType, val amount: Int)
    class Prediction(val times: Int, val items: List<Item>)
}