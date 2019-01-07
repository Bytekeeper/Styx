package org.styx.tasks

import org.styx.Context.command
import org.styx.Context.find
import org.styx.Context.recon
import org.styx.Context.reserve
import org.styx.Context.strategy
import org.styx.FlowStatus
import org.styx.Task
import org.styx.UnitQueries
import org.styx.orNull

class Attacking() : Task() {
    override val priority: Int = 3

    override fun performExecute(): FlowStatus {
        val tilePosition = recon.enemyBases().firstOrNull() ?: return FlowStatus.RUNNING

        val enemyAttackers = UnitQueries.MyUnitFinder(find.enemyUnits.filter { it.type.canAttack() })
        reserve.units.filter { it.type.canAttack() && it.type.canMove() && (!it.type.isWorker || strategy.allIn) }
                .forEach { attacker ->
                    val enemyToAttack = enemyAttackers.closestTo(attacker.x, attacker.y).orNull()
                            ?: find.enemyUnits.closestTo(attacker.x, attacker.y).orNull()
                    if (enemyToAttack != null) {
                        if (enemyToAttack != attacker.target)
                            command.attack(attacker, enemyToAttack)
                    } else {
                        val targetPosition = recon.hiddenEnemies.minBy { attacker.getDistance(it.lastKnownPosition) }?.lastKnownPosition
                                ?: tilePosition.toPosition()
                        command.attack(attacker, targetPosition)
                    }
                    reserve.reserveUnit(attacker)
                }
        return FlowStatus.RUNNING
    }
}
