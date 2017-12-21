package org.fttbot.behavior

import bwapi.Order
import bwta.BWTA
import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import org.fttbot.FTTBot
import org.fttbot.board
import org.fttbot.layer.FUnit

class Attack : LeafTask<BBUnit>() {
    override fun execute(): Status {
        val unit = `object`.unit
        val order = board().attacking as Attacking
        if (!unit.isAttackFrame) {
            val target = order.target ?: findTargetFor(unit)
            if (target != null) {
                unit.attack(target)
            } else if (ScoutingBoard.enemyBase != null) {
                unit.attack(ScoutingBoard.enemyBase!!)
            } else {
                val nearestChokepoint = BWTA.getNearestChokepoint(unit.position)
                if (nearestChokepoint != null) {
                    unit.attack(nearestChokepoint.point)
                }
            }
        }
        return Status.RUNNING
    }

    private fun findTargetFor(unit: FUnit): FUnit? {
        return FUnit.unitsInRadius(unit.position, 300)
                .filter { it.isEnemy && unit.canAttack(it, 0, true) }
                .minBy { it.hitPoints + it.type.armor * 10}
    }

    override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Attack()

    class Guard : LeafTask<BBUnit>() {
        override fun execute(): Status = if (board().attacking != null) Status.SUCCEEDED else Status.FAILED

        override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Guard()

    }
}

class Retreat : LeafTask<BBUnit>() {
    init {
        guard = Guard()
    }

    override fun execute(): Status {
        val unit = `object`.unit

        unit.move(FTTBot.self.startLocation)
        return Status.RUNNING
    }

    override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Retreat()

    class Guard : LeafTask<BBUnit>() {
        override fun execute(): Status {
            val score = FUnit.unitsInRadius(`object`.unit.position, 400)
                    .filter { it.isCompleted }
                    .sumBy { if (it.isEnemy && it.canAttack(`object`.unit, 100)) -it.hitPoints
                    else if (it.isPlayerOwned && it.canAttack) it.hitPoints else 0
                    }
            return if (score < 20) Status.SUCCEEDED else Status.FAILED
        }

        override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Guard()
    }
}


class AttackEnemyBase : LeafTask<Unit>() {
    override fun execute(): Status {
        FUnit.myUnits().filter { it.board.attacking == null && !it.isBuilding && !it.isWorker && it.canAttack }
                .forEach { it.board.attacking = Attacking() }
        return Status.RUNNING
    }

    override fun copyTo(task: Task<Unit>?): Task<Unit> = AttackEnemyBase()
}

