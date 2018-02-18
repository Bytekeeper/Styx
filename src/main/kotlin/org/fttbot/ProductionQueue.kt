package org.fttbot

import org.fttbot.behavior.Construction
import org.fttbot.behavior.ResumeConstruction
import org.fttbot.info.board
import org.fttbot.task.Resources
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.Addon
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker
import java.util.*
import java.util.logging.Logger

object ProductionQueue {
    private val LOG = Logger.getLogger(this::class.java.simpleName)
    private val queue = ArrayDeque<BOItem>()
    private val ordered = HashSet<BOItem>()
    private val pendingBuildings = ArrayDeque<PendingBuilding>()

    val pendingWithMissingBuilder
        get() = pendingBuildings.filter {
            !it.producer.exists() ||
                    it.producer.board.goal !is Construction && it.producer.board.goal !is ResumeConstruction
        }
    val reservedResourcesForPending: Resources
        get() {
            var reservedGas = 0
            var reservedMinerals = 0

            pendingBuildings
                    .forEach {
                        reservedMinerals += it.type.mineralPrice()
                        reservedGas += it.type.gasPrice()
                    }
            return Resources(minerals = reservedMinerals, gas = reservedGas)
        }


    val hasItems get() = !queue.isEmpty()
    val nextItem get() = queue.peek()

    fun onFrame() {
        ordered.clear()
    }

    fun prepend(unit: BOUnit) {
        queue.offerFirst(unit)
    }

    fun setInProgress(item: BOItem, producer: PlayerUnit) {
        queue.remove(item)
        ordered.add(item)
        if (item is BOUnit && item.type.isBuilding && !item.type.isAddon) {
            pendingBuildings.offer(PendingBuilding(producer as Worker<*>, item.type))
        }
    }

    fun onStarted(unit: PlayerUnit) {
        if (unit is Building && unit !is Addon) {
            val startedConstruction = pendingBuildings.firstOrNull {
                val goal = it.producer.board.goal as Construction
                goal.position == unit.tilePosition
            } ?: return // null, if this is a building that was given at the start of the game
            with(startedConstruction.producer.board.goal as Construction) {
                started = true
                building = unit
            }
            pendingBuildings.remove(startedConstruction)
        }
    }

    fun cancel(pending: PendingBuilding) {
        pendingBuildings.remove(pending)
    }

    fun hasEnqueued(tech: TechType) = (queue + ordered).any { it is BOResearch && it.type == tech }
    fun hasEnqueued(upgrade: UpgradeType) = (queue + ordered).any { it is BOUpgrade && it.type == upgrade }
    fun hasEnqueued(unit: UnitType) = (queue + ordered).any { it is BOUnit && it.type == unit } ||
            if (unit.isBuilding && !unit.isAddon) pendingBuildings.any { it.type == unit } else false

    fun enqueue(items: List<BOItem>) {
        queue.addAll(items)
    }

    fun enqueue(item: BOItem) {
        queue.add(item)
    }

    fun cancelNextItem() {
        queue.pop()
    }
}

class PendingBuilding(val producer: Worker<*>, val type: UnitType)