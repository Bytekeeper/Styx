package org.fttbot.search

import org.fttbot.info.allRequiredUnits
import org.fttbot.sim.GameState
import org.openbw.bwapi4j.type.UnitType
import kotlin.math.max

class DFS(val initialState: GameState, val units: Map<UnitType, Int>) {
    private var requiredSet: Set<UnitType>
    private val visited = HashMap<Key, Key>()
    var best : GameState? = null
    var end : Long = 0

    init {
        requiredSet = units.keys.map { it.allRequiredUnits() }.flatten().toSet()
    }

    fun run(timeMS: Long): GameState? {
        best = null
        end = System.currentTimeMillis() + timeMS
        visited.clear()
        return search(initialState)
    }

    private fun search(state: GameState): GameState? {
        if (System.currentTimeMillis() >= end) return null
        val finishedAt = state.finishedAt
//        val key = Key(state, finishedAt)
//        val old = visited[key]
//        if (old != null && old.endFrame <= finishedAt && old.minerals <= state.minerals && old.gas <= state.gas)
//            return null
//        visited[key] = key
        if (finishedAt >= best?.frame ?: Int.MAX_VALUE) return null
        if (units.all { (ut, num) -> state.unitsOfType(ut).size >= num }) {
            state.finish()
            return state
        }
        val supplyNeeded = units.map { (u, s) -> max(s - state.unitsOfType(u).size, 0) * u.supplyRequired() }.sum()
        val canBuildSupply = state.supplyTotal + state.pendingSupply < state.supplyUsed + supplyNeeded

        (requiredSet + state.race.supplyProvider).filter { (!it.isRefinery || state.refineries == 0)
                && (it.supplyProvided() == 0 || canBuildSupply)
                && (state.unitsOfType(it).size < units[it] ?: Int.MAX_VALUE)
                && state.isValid(it) }
                .shuffled()
                .sortedBy {units[it] ?: 1 <= state.unitsOfType(it).size }
                .forEach { ut ->
                    val cpy = state.copy()
                    if (ut.isBuilding) cpy.build(ut) else cpy.train(ut)
                    searchAndCompare(cpy)
                }
        return best
    }

    private fun searchAndCompare(cpy: GameState) {
        val res = search(cpy)
        if (res?.frame ?: Int.MAX_VALUE < best?.frame ?: Int.MAX_VALUE) {
            best = res
        }
    }

    class Key(state: GameState, val endFrame : Int) {
        val units = state.units.map { (k, v) -> k to v.size }.toMap()
        val minerals = state.minerals
        val gas = state.gas

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Key

            if (units != other.units) return false

            return true
        }

        override fun hashCode(): Int {
            return units.hashCode()
        }
    }

}