package org.fttbot.estimation

import org.fttbot.import.FUnitType
import kotlin.math.abs

object BOPrediction {
    val predictors = HashMap<Item, List<Prediction>>()

    fun bestMatch(type: FUnitType, amount: Int) =
            predictors.keys.filter { it.type == type }.minBy { abs(it.amount - amount) }


    class Item(val type: FUnitType, val amount: Int)
    class Prediction(val times: Int, val items: List<Item>)
}