package org.fttbot

import org.openbw.bwapi4j.TilePosition
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
    val queue = ArrayDeque<BOItem>()
    private val ordered = HashSet<BOItem>()

    val hasItems get() = !queue.isEmpty()
    val nextItem : BOItem? get() = queue.peek()

    fun onFrame() {
        ordered.clear()
    }

    fun prepend(unit: BOUnit) {
        queue.offerFirst(unit)
    }

    fun setInProgress(item: BOItem, producer: PlayerUnit? = null, tilePosition: TilePosition? = null) {
        queue.remove(item)
        ordered.add(item)
    }

    fun onStarted(unit: PlayerUnit) {
    }

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

class PendingBuilding(var producer: Worker, val type: UnitType, val position: TilePosition)