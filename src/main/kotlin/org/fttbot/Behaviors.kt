package org.fttbot

import com.badlogic.gdx.ai.btree.BehaviorTree
import com.badlogic.gdx.ai.btree.LeafTask
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.ai.btree.branch.DynamicGuardSelector
import com.badlogic.gdx.ai.btree.branch.Sequence
import com.badlogic.gdx.ai.btree.leaf.Success
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeLibrary
import org.fttbot.behavior.*
import org.fttbot.layer.FUnit

fun <T> Task<T>.board(): T = this.`object`

fun <E, T : Task<E>> T.guardedBy(guard: Task<E>): T {
    this.guard = guard
    return this
}


object UnitBehaviors {
    private val btlib = BehaviorTreeLibrary()
    private val unitBehavior = HashMap<FUnit, BehaviorTree<*>>()

    private val gatherMinerals get() = Sequence(ReturnResource(), GatherMinerals())
    private val construct: Sequence<BBUnit>
        get() = Sequence(ReturnResource(), SelectConstructionSiteAsTarget(), MoveToPosition(30.0), Construct())
                .guardedBy(ShouldConstruct())

    init {
        btlib.registerArchetypeTree(Behavior.WORKER.name, BehaviorTree(DynamicGuardSelector(construct, Scout(), gatherMinerals)))
        btlib.registerArchetypeTree(Behavior.TRAINER.name, BehaviorTree(Train()))
        btlib.registerArchetypeTree(Behavior.COMBAT_UNIT.name, BehaviorTree(DynamicGuardSelector(Retreat(), Attack())))
    }

    fun createTreeFor(unit: FUnit): BehaviorTree<*> {
        val behavior = when {
            unit.isWorker -> createTreeFor(Behavior.WORKER, BBUnit.of(unit))
            unit.type.canProduce -> createTreeFor(Behavior.TRAINER, BBUnit.of(unit))
            unit.canAttack -> createTreeFor(Behavior.COMBAT_UNIT, BBUnit.of(unit))
            else -> BehaviorTree<Unit>(Success())
        }
        unitBehavior[unit] = behavior
        return behavior
    }

    fun step() {
        unitBehavior.values.forEach { it.step() }
    }

    private fun createTreeFor(behavior: Behavior, blackboard: Any) = btlib.createBehaviorTree(behavior.name, blackboard)

    enum class Behavior() {
        WORKER,
        TRAINER,
        COMBAT_UNIT
    }
}

abstract class LT<T> : LeafTask<T>() {
    override fun cloneTask(): Task<T> {
        return cpy()
    }

    abstract fun cpy(): Task<T>

    final override fun copyTo(task: Task<T>?): Task<T> = throw IllegalStateException()
}