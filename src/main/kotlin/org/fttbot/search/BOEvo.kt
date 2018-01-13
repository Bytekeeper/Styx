package org.fttbot.search

import org.fttbot.sim.GameState
import org.openbw.bwapi4j.type.UnitType
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max

class BOEvo(state: GameState) {
//    companion object {
//        private val prng = Random()
//    }
//
//    val pop = ArrayList<BuildOrder>()
//
//    init {
//        init(pop, state, 50)
//    }
//
//    fun evo(state: GameState): List<BOItem> {
//        for (i in 0..5) {
//            pop.forEach {
//                if (it.eval == null) {
//                    val s = state.copy()
//                    for (i in 0 until it.items.size) {
//                        val item = it.items[i]
//                        val unit = item.unit
//                        if (unit == null || !s.isValid(unit)) break
//                        if (unit.isBuilding) s.build(unit) else s.train(unit)
//                    }
//                    it.eval = eval(s)
//                }
//            }
//            pop.sortByDescending { it.eval }
//            repeat(25) { pop.removeAt(pop.size - 1) }
//            proCreate(state, pop)
//        }
//        return pop[0].items
//    }
//
//    private fun eval(s: GameState): Double {
//        return s.units.map { (k, v) ->
//            k.groundWeapon().damageAmount() * v.size }.sum().toDouble() / (s.frame + (s.units.values.flatten().map { max(0, it.availableAt - s.frame) }.max() ?: 0) + 1000)
//    }
//
//    private fun proCreate(state: GameState, pop: MutableList<BuildOrder>) {
//        pop.map {
//            val s = state.copy()
//            val a = it.items
//            val b = pop[prng.nextInt(pop.size)].items
//            val result = (0 until max(a.size, b.size))
//                    .filter { prng.nextDouble() > 0.01 }
//                    .map { i ->
//                        if (prng.nextDouble() < 0.05) s.randomValidItem(prng)
//                        else if (i >= a.size) b[i]
//                        else if (i >= b.size) a[i]
//                        else if (prng.nextBoolean() && s.isValid(a[i])) a[i]
//                        else b[i]
//                    }
//                    .filterNotNull().toMutableList()
//            if (prng.nextDouble() < 0.2) {
//                val s = state.copy()
//                val index = if (result.isEmpty()) 0 else prng.nextInt(result.size)
//                for (i in 0 until index) {
//                    if (s.isValid(result[i]))
//                        s.build(result[i])
//                    else {
//                        result.subList(i, result.size).clear()
//                        break
//                    }
//                }
//                val validItem = s.randomValidItem(prng)
//                if (validItem != null) result.add(index, validItem)
//            }
//            result
//        }.map { if (!it.isEmpty()) it else randomItems(state.copy()) }
//                .forEach { pop.add(BuildOrder(it)) }
//    }
//
//    private fun init(pop: MutableList<BuildOrder>, state: GameState, popSize: Int = 10) {
//        for (i in 0 until popSize) {
//            val clone = state.copy()
//            val items = randomItems(clone)
//            pop.add(BuildOrder(items))
//        }
//    }
//
//    private fun randomItems(clone: GameState): MutableList<BOItem> =
//            IntRange(0, prng.nextInt(5) + 3)
//                    .map { clone.randomValidItem(prng) }
//                    .filterNotNull()
//                    .map {
//                        clone.build(it)
//                        it
//                    }.toMutableList()
//
//    class BuildOrder(val items: MutableList<BOItem>, var eval: Double? = null)
//
//    class BOItem(val unit: UnitType?)
}