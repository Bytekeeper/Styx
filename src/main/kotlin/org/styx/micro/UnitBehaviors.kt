package org.styx.micro

import org.bk.ass.bt.TreeNode
import org.styx.SUnit
import org.styx.plus
import org.styx.times

class AvoidCombat(private val unit: () -> SUnit) : TreeNode() {
    override fun exec() {
        val unit = unit()
        if (!unit.unitType.canMove() || unit.threats.isEmpty())
            success()
        else {
            val force = Potential.avoidDanger(unit, 96) +
                    Potential.collisionRepulsion(unit) * 0.2
            Potential.apply(unit, force)
            running()
        }
    }
}

