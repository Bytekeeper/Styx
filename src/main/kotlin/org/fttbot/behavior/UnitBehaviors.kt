package org.fttbot.behavior

import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task

class Attack : LeafTask<BBUnit>() {
    override fun execute(): Status {
        val unit = `object`.unit
        val order = `object`.order as BBUnit.Attacking
        if (!unit.isAttackFrame) {
            unit.attack(order.target)
        }
        return Status.RUNNING
    }

    override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Attack()

    class Guard : LeafTask<BBUnit>() {
        override fun execute(): Status = if (`object`.order is BBUnit.Attacking) Status.SUCCEEDED else Status.FAILED

        override fun copyTo(task: Task<BBUnit>?): Task<BBUnit> = Guard()

    }
}