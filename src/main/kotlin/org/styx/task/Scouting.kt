package org.styx.task

import bwapi.UnitType
import org.bk.ass.bt.TreeNode
import org.styx.*
import org.styx.Styx.bases
import org.styx.Styx.game
import org.styx.Styx.units
import org.styx.global.Base
import org.styx.micro.Force

object Scouting : TreeNode() {
    private val ovis = UnitLocks { UnitReservation.availableItems.filter { it.unitType == UnitType.Zerg_Overlord } }

    override fun exec() {
        running()
        ovis.reacquire()
        if (ovis.item.isEmpty())
            return
        val remaining = ovis.item.toMutableList()

        Styx.squads.squads.filter {
            it.mine.count { it.unitType.isDetector } < 3
                    && it.enemies.any { it.cloaked }
        }.take(remaining.size)
                .forEach { squad ->
                    val ovi = remaining.minBy { it.distanceTo(squad.center) }!!
                    remaining -= ovi
                    val force = Force().avoidDanger(ovi, 200).attract(ovi, squad.center)
                    force.apply(ovi)
                }

        remaining.removeIf { ovi ->
            val enemy = units.enemy.nearest(ovi.x, ovi.y, 800) { it.hasWeaponAgainst(ovi) && it.flying }
                    ?: return@removeIf false
            val bestAlly = units.mine.nearest(ovi.x, ovi.y, 600) { it.hasWeaponAgainst(enemy) } ?: return@removeIf false
            val force = Force().pursuit(ovi, bestAlly)
            force.apply(ovi)
            true
        }

        val comp = compareBy<Base> { it.lastSeenFrame ?: 0 }
                .thenBy { other -> !other.isStartingLocation }
                .thenBy { other -> bases.myBases.map { other.center.getApproxDistance(it.center) }.min() }
        for (base in bases.bases.filter { !game.isVisible(it.centerTile) }.sortedWith(comp)) {
            val ovi = remaining.minBy { it.distanceTo(base.center) } ?: return
            val force = Force().avoidDanger(ovi, 300).attract(ovi, base.center)
            force.apply(ovi)

            remaining -= ovi
        }
        return
    }

}