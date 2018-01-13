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
import org.fttbot.decision.Utilities
import org.fttbot.decision.UtilitySelector
import org.fttbot.decision.UtilityTask
import org.fttbot.info.isWorker
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

    private val gatherResources
        get() = DynamicGuardSelector(ReturnResource().guardedBy(ShouldReturnResource()),
                Selector(GatherMinerals(), GatherGas()))
    private val construct: Task<BBUnit>
        get() = Selector(Sequence(ReturnResource(), SelectConstructionSiteAsTarget(), MoveToPosition(120.0), Construct())
                , AbortConstruct())
    private val defendWithWorker: Task<BBUnit>
        get() = Sequence(SelectBestAttackTarget(), Attack())

    init {
        btlib.registerArchetypeTree(Behavior.WORKER.name, BehaviorTree(
            UtilitySelector(UtilityTask(defendWithWorker, Utilities::defend),
                    UtilityTask(construct, Utilities::construct),
                    UtilityTask(Scout(), Utilities::scout),
                    UtilityTask(gatherResources, Utilities::gather),
                    UtilityTask(MoveToEnemyBase(), Utilities::runaway))
        ))
        btlib.registerArchetypeTree(Behavior.TRAINER.name, BehaviorTree(Selector(TrainOrAddon(), ResearchOrUpgrade())))
        btlib.registerArchetypeTree(Behavior.COMBAT_UNIT.name, BehaviorTree(
                DynamicGuardSelector(
                        Selector(Attack().guardedBy(SelectRetreatAttackTarget()), Retreat()).guardedBy(UnfavorableSituation()),
                        FallBack().guardedBy(OnCooldownWithBetterPosition()),
                        Sequence(Attack(), Sequence(FindGoodAttackPosition(), MoveToPosition()).guardedBy(OnCooldownWithBetterPosition())).guardedBy(SelectBestAttackTarget()),
                        MoveToEnemyBase())))
    }

    fun hasBehaviorFor(unit: PlayerUnit) = unitBehavior.containsKey(unit)

    fun createTreeFor(unit: PlayerUnit): BehaviorTree<*> {
        val behavior = when {
            (unit is MobileUnit) && unit.isWorker -> {
                val b = BBUnit(unit)
                unit.userData = b
                createTreeFor(Behavior.WORKER, b)
            }
            unit is ResearchingFacility ||
            unit is TrainingFacility -> {
                val b = BBUnit(unit)
                unit.userData = b
                createTreeFor(Behavior.TRAINER, b)
            }
            unit is Armed -> {
                val b = BBUnit(unit)
                unit.userData = b
                createTreeFor(Behavior.COMBAT_UNIT, b)
            }
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
