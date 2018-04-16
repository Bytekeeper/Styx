package org.fttbot.search

import org.fttbot.GameState
import org.fttbot.info.allRequiredUnits
import org.fttbot.info.whatNeedsToBeBuild
import org.openbw.bwapi4j.type.UnitType
import java.lang.Integer.compare
import java.lang.Integer.max
import java.util.*

class AStarBO(val initialState: GameState, val units: Map<UnitType, Int>) {
    private val openSet = PriorityQueue<Node>()
    private val requiredSet: Set<UnitType>
    private var supplyTotalRequired: Int

    init {
        openSet.offer(Node(initialState, 0))
        requiredSet = units.keys.map { it.allRequiredUnits() }.flatten().toSet()
        supplyTotalRequired = max(0, units.map { (ut, v) -> (ut.supplyRequired() - ut.supplyProvided()) * v }.sum() + initialState.supplyUsed)
    }

    fun run(): GameState {
        while (true) {
            val node = openSet.poll();
            val state = node.state

            if (units.all { e -> (state.units[e.key]?.size ?: 0) >= e.value }) {
                if (units.any { e -> state.completedUnits(e.key).size < e.value }) {
                    val cpy = state.copy()
                    cpy.finish()
                    openSet.offer(Node(cpy, cpy.frame))
                    continue
                } else {
                    return node.state
                }
            }

            if (state.supplyTotal + state.pendingSupply < supplyTotalRequired) {
                val cpy = state.copy()
                val supplyProvider = state.race.supplyProvider
                if (supplyProvider.isBuilding) cpy.build(supplyProvider) else cpy.train(supplyProvider)
                val eval = eval(cpy)
                openSet.offer(Node(cpy, cpy.frame + eval))
            }
            requiredSet.forEach { ut ->
                if (!state.isValid(ut)) return@forEach
                if (ut.isRefinery && state.refineries > 0) return@forEach
                val u = units[ut]
                if (u != null && state.unitsOfType(ut).size >= u) return@forEach
                val cpy = state.copy()
                if (ut.isBuilding) cpy.build(ut) else cpy.train(ut)
                val eval = eval(cpy)
                openSet.offer(Node(cpy, cpy.frame + eval))
            }
        }
    }

    fun eval(state: GameState) : Int {
        val cpy = state.copy(minerals = 100000, gas = 100000)
        units.forEach { ut, amount ->

        }
        return 0
    }

    class Node(val state: GameState, val f: Int) : Comparable<Node> {
        override fun compareTo(other: Node): Int = compare(f, other.f)
    }
}