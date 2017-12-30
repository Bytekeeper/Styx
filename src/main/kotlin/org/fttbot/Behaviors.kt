package org.fttbot

import com.badlogic.gdx.ai.btree.BehaviorTree
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.ai.btree.branch.DynamicGuardSelector
import com.badlogic.gdx.ai.btree.branch.Selector
import com.badlogic.gdx.ai.btree.branch.Sequence
import com.badlogic.gdx.ai.btree.leaf.Success
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeLibrary
import org.fttbot.behavior.*
import org.fttbot.behavior.Scout
import org.fttbot.layer.isWorker
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit

fun <T> Task<T>.board(): T = this.`object`

fun <E, T : Task<E>> T.guardedBy(guard: Task<E>): T {
    this.guard = guard
    return this
}


object UnitBehaviors {
    private val btlib = BehaviorTreeLibrary()
    private val unitBehavior = HashMap<PlayerUnit, BehaviorTree<*>>()

    private val gatherMinerals
        get() = DynamicGuardSelector(ReturnResource().guardedBy(ShouldReturnResource()),
                Selector(GatherMinerals(), GatherGas()))
    private val construct: Task<BBUnit>
        get() = Selector(Sequence(ReturnResource(), SelectConstructionSiteAsTarget(), MoveToPosition(120.0), Construct())
                , AbortConstruct()).guardedBy(ShouldConstruct())
    private val defendWithWorker: Task<BBUnit>
        get() = Sequence(SelectBestAttackTarget(), Attack()).guardedBy(EmergencySituation().guardedBy(ShouldDefendWithWorker()))

    init {
        btlib.registerArchetypeTree(Behavior.WORKER.name, BehaviorTree(DynamicGuardSelector(defendWithWorker, construct, Scout(), gatherMinerals)))
        btlib.registerArchetypeTree(Behavior.TRAINER.name, BehaviorTree(TrainOrAddon()))
        btlib.registerArchetypeTree(Behavior.COMBAT_UNIT.name, BehaviorTree(
                DynamicGuardSelector(
                        Selector(Attack().guardedBy(SelectRetreatAttackTarget()), Retreat()).guardedBy(UnfavorableSituation()),
                        FallBack().guardedBy(ShouldFallBack()),
                        Sequence(Attack(), FindGoodAttackPosition(), MoveToPosition()).guardedBy(SelectBestAttackTarget()),
                        MoveToEnemyBase())))
    }

    fun hasBehaviorFor(unit: PlayerUnit) = unitBehavior.containsKey(unit)

    fun createTreeFor(unit: PlayerUnit): BehaviorTree<*> {
        val behavior = when {
            (unit is MobileUnit) && unit.isWorker -> createTreeFor(Behavior.WORKER, BBUnit.of(unit))
            unit is TrainingFacility -> createTreeFor(Behavior.TRAINER, BBUnit.of(unit))
            unit is Armed -> createTreeFor(Behavior.COMBAT_UNIT, BBUnit.of(unit))
            else -> BehaviorTree<Unit>(Success())
        }
        unitBehavior[unit] = behavior
        return behavior
    }

    fun removeBehavior(unit: PlayerUnit) {
        unitBehavior.remove(unit)
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
