package org.styx.micro

import org.bk.ass.bt.TreeNode
import org.styx.SUnit

class AvoidCombat(private val unit: () -> SUnit) : TreeNode() {
    override fun exec() {
        val unit = unit()
        if (!unit.unitType.canMove() || unit.threats.isEmpty())
            success()
        else {
            val force = Force().avoidDanger(unit, 96)
                    .collisionRepulsion(unit, 0.2)
            force.apply(unit)
            running()
        }
    }
}

