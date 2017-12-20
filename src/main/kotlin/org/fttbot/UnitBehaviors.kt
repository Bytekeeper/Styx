package org.fttbot

import com.badlogic.gdx.ai.btree.BehaviorTree
import com.badlogic.gdx.ai.btree.branch.DynamicGuardSelector
import com.badlogic.gdx.ai.btree.leaf.Success
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeLibrary
import org.fttbot.behavior.*
import org.fttbot.layer.FUnit

object UnitBehaviors {
    private val btlib = BehaviorTreeLibrary()
    private val unitBehavior = HashMap<FUnit, BehaviorTree<*>>()

    init {
        btlib.registerArchetypeTree(Behavior.WORKER.name, BehaviorTree(DynamicGuardSelector(Construct(), GatherMinerals())))
        btlib.registerArchetypeTree(Behavior.TRAINER.name, BehaviorTree(Train()))
    }

    fun createTreeFor(unit: FUnit): BehaviorTree<*> {
        val behavior = when {
            unit.isWorker -> createTreeFor(Behavior.WORKER, BBUnit.of(unit))
            unit.type.canProduce -> createTreeFor(Behavior.TRAINER, BBUnit.of(unit))
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
        TRAINER
    }
}