package org.fttbot.task

import com.badlogic.gdx.math.Vector2
import org.fttbot.Potential
import org.fttbot.info.canBeAttacked
import org.fttbot.info.potentialAttackers
import org.openbw.bwapi4j.unit.MobileUnit

class AvoidDamage(val unit: MobileUnit) : Task() {
    override val utility: Double
        get() = if (unit.canBeAttacked(64)) 0.7 else 0.0

    override fun processInternal(): TaskStatus {
        val force = Vector2()
        if (unit.isFlying) {
            if (unit.potentialAttackers(64).none { it.isFlying }) {
                Potential.addWallAttraction(force, unit)
            }
        } else {
            Potential.addWallRepulsion(force, unit)
        }
        Potential.addThreatRepulsion(force, unit)
        val pos = Potential.wrapUp(unit, force)
        if (unit.targetPosition.getDistance(pos) > 8) {
            unit.move(pos)
        }
        return TaskStatus.RUNNING
    }
}