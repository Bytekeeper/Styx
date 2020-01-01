package org.styx.squad

import org.bk.ass.bt.TreeNode
import org.styx.*
import org.styx.action.BasicActions

class SquadScout(private val squad: SquadBoard) : TreeNode() {
    private val attackerLock = UnitLocks {
        Styx.resources.availableUnits.filter {
            squad.mine.contains(it) &&
                    !it.unitType.isWorker &&
                    it.unitType.canMove() &&
                    it.unitType.canAttack()
        }
    }

    override fun exec() {
        running()
        if (Styx.bases.potentialEnemyBases.isEmpty())
            return
        attackerLock.reacquire()
        if (attackerLock.item.isEmpty())
            return
        val targetBase = Styx.bases.enemyBases.firstOrNull { it.lastSeenFrame == null }
                ?: Styx.bases.potentialEnemyBases.firstOrNull() ?: run { failed(); return }
        squad.task = "Scout"
        attackerLock.item.forEach {
            BasicActions.move(it, targetBase.center)
        }
    }
}