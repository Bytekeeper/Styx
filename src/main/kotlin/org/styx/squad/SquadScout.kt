package org.styx.squad

import org.styx.*
import org.styx.action.BasicActions

class SquadScout(private val squad: Squad) : BTNode() {
    private val attackerLock = UnitLocks {
        Styx.resources.availableUnits.filter {
            squad.mine.contains(it) &&
                    !it.unitType.isWorker &&
                    it.unitType.canMove() &&
                    it.unitType.canAttack()
        }
    }

    override fun tick(): NodeStatus {
        if (Styx.bases.potentialEnemyBases.isEmpty())
            return NodeStatus.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty())
            return NodeStatus.RUNNING
        val targetBase = Styx.bases.enemyBases.firstOrNull { it.lastSeenFrame == null }
                ?: Styx.bases.potentialEnemyBases.minBy {
                    it.center.getApproxDistance(squad.myCenter) + (Styx.units.enemy.nearest(it.center)?.framesToTravelTo(it.center)
                            ?: Int.MAX_VALUE)
                }!!
        squad.task = "Scout"
        attackerLock.units.forEach {
            BasicActions.move(it, targetBase.center)
        }
        return NodeStatus.RUNNING
    }
}