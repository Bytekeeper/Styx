package org.fttbot.search

import org.fttbot.GameState
import org.fttbot.info.allRequiredUnits
import org.openbw.bwapi4j.type.Race
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import java.util.*
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

const val EPS = 1E-7

class MCTS(val units: Map<UnitType, Int>, val tech: Set<TechType>, val upgrades: Map<UpgradeType, Int>, var race: Race) {
    var root: Node = Node()
    private val requiredSet: Set<UnitType>
    private val prng = Random()
    private var bestFrameDepth = Int.MAX_VALUE
    var bestNode: Node? = null

    private var unitsToTest: Set<UnitType>

    init {
        requiredSet = (units.keys.map { it.allRequiredUnits() }.flatten().filter { it != UnitType.Terran_Command_Center } +
                tech.map { it.requiredUnit() } + tech.map { it.whatResearches() } + upgrades.keys.map { it.whatUpgrades() }
                + upgrades.entries.flatMap { (ut, lvl) -> (0 until lvl).map { ut.whatsRequired(it) } + ut.whatUpgrades() }).toSet() - UnitType.None
        unitsToTest = requiredSet + race.supplyProvider
    }

    fun reset() {
        bestFrameDepth = Int.MAX_VALUE
        bestNode = null
    }

    fun restart() {
        reset()
        root = Node()
    }

    fun relocateTo(nextRoot: Node) {
        reset()
        root = nextRoot
        root.parent = null
    }

    fun step(state: GameState) {
        val cpy = state.copy()
        var node = root
        do {
            val next = node.select(prng, node.maxFrames)
            if (next != null) {
                node = next
                next.move?.applyTo(cpy)
            }
        } while (next != null)
        if (cpy.finishedAt > bestFrameDepth || node.children?.size == 0) {
            removeNode(node)
        } else {
            val result = if (notDone(cpy)) {
                expand(cpy, node)
                if (node.children?.isEmpty() == false) {
                    try {
                        simulate(cpy, node.select(prng, node.maxFrames)!!)
                    } catch (e: IllegalStateException) {
                        Int.MAX_VALUE
                    }
                } else 0
            } else {
                val finishedAt = cpy.finishedAt
                bestFrameDepth = finishedAt
                bestNode = node
                finishedAt

            }
            backup(result, node)
        }
    }

    private fun removeNode(node: Node) {
        var currentNode = node
        var parent = node.parent

        while (parent != null) {
            parent.children!!.remove(currentNode)
            currentNode = parent
            parent = currentNode.parent
            if (parent?.children?.size ?: 2 > 1) break
        }
    }

    private fun backup(result: Int, node: Node) {
        var current: Node? = node;
        node.frames = result
        while (current != null) {
            current.frames = min(current.frames, result)
            current.plays++
            current = current.parent
        }
    }

    private fun simulate(state: GameState, select: Node): Int {
        select.move?.applyTo(state)
        do {
            val toBuild = requiredSet.firstOrNull { state.isValid(it) && state.unitsOfType(it).isEmpty() }
            if (toBuild != null) {
                if (toBuild.isBuilding) state.build(toBuild) else state.train(toBuild)
            }
        } while (toBuild != null)
        upgrades.forEach { ut, lvl ->
            ((state.upgrades[ut]?.level ?: 0) until lvl).forEach { state.upgrade(ut) }
        }
        do {
            var toBuild = units.entries.firstOrNull { (u, a) -> state.isValid(u) && state.unitsOfType(u).size < a }?.key
            if (toBuild == null && notDone(state)) {
                toBuild = race.supplyProvider
            }
            if (toBuild != null) {
                if (toBuild.isBuilding) state.build(toBuild) else state.train(toBuild)
            }
        } while (toBuild != null)
        val finishedAt = state.finishedAt
        if (finishedAt < bestFrameDepth) {
            bestFrameDepth = finishedAt
            bestNode = select
        }
        return finishedAt
    }

    private fun notDone(state: GameState) = units.any { (u, a) -> state.unitsOfType(u).size < a } ||
            upgrades.any { (ut, lvl) -> state.getUpgradeLevel(ut) < lvl }

    private fun expand(state: GameState, node: Node) {
        if (node.children != null) return
        node.children = (unitsToTest.filter { state.isValid(it) }
                .map { Node(node, UnitMove(it)) } +
                upgrades.filter { (ut, lvl) -> state.getUpgradeLevel(ut) < lvl && state.isValid(ut) }.map { (ut, _) -> Node(node, UpgradeMove(ut)) })
                .toMutableList()
    }

    class Node(var parent: Node? = null, val move: Move? = null, var frames: Int = 0, var plays: Int = 0, var children: MutableList<Node>? = null) {

        val maxFrames get() = children?.map { it.frames }?.max() ?: Int.MAX_VALUE
        fun select(prng: Random, maxFrames: Int): Node? = children?.maxBy { n -> (1.0 - n.frames / (maxFrames + EPS)) + 0.3 * sqrt(ln(plays + 1.0) / (n.plays + 1)) + prng.nextDouble() * EPS }

        override fun toString(): String = "move: $move frames: $frames visits: $plays"
    }

    interface Move {

        fun applyTo(state: GameState)
    }

    class UnitMove(val unit: UnitType) : Move {
        override fun applyTo(state: GameState) {
            if (unit.isBuilding) state.build(unit) else state.train(unit)
        }

        override fun toString(): String = "$unit"
    }

    class UpgradeMove(val upgrade: UpgradeType) : Move {
        override fun applyTo(state: GameState) {
            state.upgrade(upgrade)
        }

        override fun toString(): String = "$upgrade"

    }
}