package org.styx.tasks

import bwapi.Unit
import bwem.Base
import org.styx.Context.command
import org.styx.Context.game
import org.styx.Context.map
import org.styx.Context.reserve
import org.styx.FlowStatus
import org.styx.Task

class Scouting() : Task() {
    override val priority: Int = 0
    private val scoutAt = mutableMapOf<Base, Unit>()

    override fun performExecute(): FlowStatus {
        scoutAt.entries.removeIf { (b, u) -> !u.exists() || !reserve.units.contains(u) || game.isVisible(b.location) }
        reserve.reserveUnits(scoutAt.values)

        map.bases.filter { !scoutAt.containsKey(it) && !game.isVisible(it.location) }
                .forEach { base ->
                    val scout = (reserve.units.filter { it.type.canMove() }.minBy { it.getDistance(base.center) }
                            ?: return@forEach)
                    scoutAt[base] = scout
                    reserve.reserveUnit(scout)
                }
        scoutAt.forEach { b, u -> command.move(u, b.center) }
        return FlowStatus.RUNNING
    }
}
