package org.styx.micro

import org.styx.SUnit
import org.styx.Styx.geography
import org.styx.action.BasicActions
import org.styx.noObstacle
import org.styx.plus
import org.styx.times

object Combat {
    fun attack(attacker: SUnit, enemy: SUnit) {
        if (enemy.visible) {
            val shouldKite = attacker.isOnCoolDown &&
                    attacker.canMoveWithoutBreakingAttack &&
                    attacker.maxRangeVs(enemy) > enemy.maxRangeVs(attacker) &&
                    enemy.inAttackRange(attacker, 64) &&
                    attacker.inAttackRange(enemy, -16)
            if (shouldKite) {
                val force = Potential.repelFrom(attacker, enemy) +
                        Potential.collisionRepulsion(attacker)
                Potential.apply(attacker, force)
            } else {
                val relativeMovement = attacker.velocity.normalize().dot(enemy.velocity.normalize())
                if (relativeMovement < -0.3 || enemy.distanceTo(attacker) < attacker.maxRangeVs(enemy) + 32 || !attacker.flying && !geography.walkRay.noObstacle(attacker.walkPosition, enemy.walkPosition)) {
                    BasicActions.attack(attacker, enemy)
                } else {
                    val force = Potential.collisionRepulsion(attacker) * 0.4 +
                            Potential.intercept(attacker, enemy)
                    Potential.apply(attacker, force)
                }
            }
        } else
            BasicActions.move(attacker, enemy.position)

    }
}