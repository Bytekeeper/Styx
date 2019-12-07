package org.styx.task

import bwapi.UnitType
import org.styx.*
import org.styx.Styx.bases
import org.styx.Styx.game
import org.styx.action.BasicActions
import org.styx.micro.Potential

object Scouting : BTNode() {
    private val ovis = UnitLocks { Styx.resources.availableUnits.filter { it.unitType == UnitType.Zerg_Overlord } }

    override fun tick(): NodeStatus {
        ovis.reacquire()
        if (ovis.units.isEmpty()) return NodeStatus.RUNNING
        val remaining = ovis.units.toMutableList()
        for (base in bases.bases.filter { !game.isVisible(it.centerTile) }.sortedBy { it.lastSeenFrame ?: 0 }) {
            val ovi = remaining.minBy { it.distanceTo(base.center) } ?: return NodeStatus.RUNNING
            if (ovi.threats.isNotEmpty()) {
                val safety = 96 + 256 - (256 * ovi.hitPoints) / ovi.unitType.maxHitPoints()
                val force = Potential.airAttract(ovi, base.center) * 0.3 + Potential.avoidDanger(ovi, safety)
                Potential.apply(ovi, force)
            } else {
                if (Styx.units.enemy.inRadius(ovi, 300).any { it.inAttackRange(ovi, 32f) }) {
                    println("MEH")
                }
                BasicActions.move(ovi, base.center)
            }
            remaining.remove(ovi)
        }
        return NodeStatus.RUNNING
    }

}