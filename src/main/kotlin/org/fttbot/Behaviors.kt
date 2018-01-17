package org.fttbot

import org.fttbot.behavior.*
import org.fttbot.behavior.Scout
import org.fttbot.decision.Utilities
import org.fttbot.decision.UtilitySelector
import org.fttbot.decision.UtilityTask
import org.fttbot.info.isWorker
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit


object UnitBehaviors {
    private val unitBehavior = HashMap<PlayerUnit, BTree<*>>()

    private val gatherResources
        get() = MSelector(ReturnResource() onlyIf ShouldReturnResource(),
                GatherMinerals(), GatherGas())
    private val construct: Node<BBUnit>
        get() = Fallback(MSequence(ReturnResource(), SelectConstructionSiteAsTarget(), MoveToPosition(120.0), Construct())
                , AbortConstruct())
    private val defendWithWorker
        get() = Attack onlyIf SelectBestAttackTarget()

    private fun createWorkerTree(board: BBUnit) = BTree(
            Fallback(
                    UtilitySelector(UtilityTask(defendWithWorker) { u -> if (u.goal is Defending) 1.0 else 0.0 },
                            UtilityTask(construct, Utilities::construct),
                            UtilityTask(Scout(), Utilities::scout),
                            UtilityTask(gatherResources, Utilities::gather),
                            UtilityTask(MoveToPosition(96.0) onlyIf SelectRunawayPosition, Utilities::runaway),
                            UtilityTask(Repair()) { u -> if (u.goal is Repairing) 1.0 else 0.0 },
                            UtilityTask(MoveToPosition(96.0) onlyIf MoveTargetFromGoal()) { u -> if (u.goal is IdleAt) 1.0 else 0.0 },
                            UtilityTask(MoveToPosition(96.0) onlyIf SelectSafePosition()) { u -> if (u.goal is BeRepairedBy) 1.0 else 0.0 }
                    ),
                    MoveToPosition(70.0) onlyIf SelectBaseAsTarget()
            ),
            board
    )

    private fun createTrainerTree(board: BBUnit) = BTree(
            Fallback(TrainOrAddon(), ResearchOrUpgrade()), board
    )

    private fun createCombatUnitTree(board: BBUnit) = BTree(
            UtilitySelector(
                    UtilityTask(MoveToPosition(96.0) onlyIf SelectSafePosition()) { u -> if (u.goal is BeRepairedBy) 1.0 else 0.0 },
                    UtilityTask(Fallback(
                            Fallback(Attack onlyIf SelectRetreatAttackTarget(), Retreat()) onlyIf UnfavorableSituation(),
                            FallBack onlyIf OnCooldownWithBetterPosition(),
                            Sequence(Attack, MSequence(FindGoodAttackPosition(), MoveToPosition())) onlyIf SelectBestAttackTarget(),
                            MoveToEnemyBase())) { 0.5 })
            , board
    )

    fun hasBehaviorFor(unit: PlayerUnit) = unitBehavior.containsKey(unit)

    fun createTreeFor(unit: PlayerUnit): BTree<*> {
        val behavior = when {
            (unit is MobileUnit) && unit.isWorker -> {
                val b = BBUnit(unit)
                unit.userData = b
                createWorkerTree(b)
            }
            unit is ResearchingFacility ||
                    unit is TrainingFacility -> {
                val b = BBUnit(unit)
                unit.userData = b
                createTrainerTree(b)
            }
            unit is Armed -> {
                val b = BBUnit(unit)
                unit.userData = b
                createCombatUnitTree(b)
            }
            else -> BTree<Unit>(Success(), unit)
        }
        unitBehavior[unit] = behavior
        return behavior
    }

    fun removeBehavior(unit: PlayerUnit) {
        unitBehavior.remove(unit)
    }

    fun step() {
        unitBehavior.values.forEach { it.tick() }
    }

    enum class Behavior() {
        WORKER,
        TRAINER,
        COMBAT_UNIT
    }
}
