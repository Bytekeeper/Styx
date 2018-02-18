package org.fttbot

import org.fttbot.behavior.*
import org.fttbot.behavior.Scout
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
    private val resumeConstruct: Node<BBUnit>
        get() = Fallback(MSequence(ReturnResource(), ResumeConstruct), AbortConstruct())
    private val defendWithWorker
        get() = Attack onlyIf SelectBestAttackTarget()
    private val backUp get() = MoveToPosition(16.0) onlyIf SelectRunawayPosition

    private val attack get() = Sequence(Attack, MSequence(FindGoodAttackPosition(), MoveToPosition())) onlyIf SelectBestAttackTarget()

    private fun createWorkerTree(board: BBUnit) = BTree(
            Fallback(Sleep,
                    Fallback(
                            UtilitySelector(UtilityTask(defendWithWorker) { u -> if (u.goal is Defending) 1.0 else 0.0 },
                                    UtilityTask(construct, UnitUtility::construct),
                                    UtilityTask(resumeConstruct) { u -> if (u.goal is ResumeConstruction) 1.0 else 0.0 },
                                    UtilityTask(Scout(), UnitUtility::scout),
                                    UtilityTask(gatherResources, UnitUtility::gather),
                                    UtilityTask(backUp, UnitUtility::runaway),
                                    UtilityTask(Repair()) { u -> if (u.goal is Repairing) 1.0 else 0.0 },
                                    UtilityTask(MoveToPosition(96.0) onlyIf MoveTargetFromGoal) { u -> if (u.goal is IdleAt) 1.0 else 0.0 },
                                    UtilityTask(MoveToPosition(96.0) onlyIf SelectSafePosition) { u -> if (u.goal is BeRepairedBy) 1.0 else 0.0 }
                            ),
                            MoveToPosition(70.0) onlyIf SelectBaseAsTarget()
                    )),
            board
    )

    private fun createTrainerTree(board: BBUnit) = BTree(
            Fallback(ResearchingOrUpgrading), board
    )

    private fun createCombatUnitTree(board: BBUnit) = BTree(
            Fallback(Sleep,
                    Fallback(
                            Sequence(IsTrue { it.goal is BeRepairedBy }, SelectSafePosition, MoveToPosition(64.0)),
                            attack onlyIf IsTrue { it.goal is Defending },
                            Fallback(Attack onlyIf SelectRetreatAttackTarget(), backUp) onlyIf UnfavorableSituation(),
                            backUp onlyIf OnCooldownWithBetterPosition,
                            attack,
                            MoveToEnemyBase()
                    ))
//            UtilitySelector(
//                    UtilityTask(MoveToPosition(96.0) onlyIf SelectSafePosition()) { u -> if (u.goal is BeRepairedBy) 1.0 else 0.0 },
//                    UtilityTask(createAttackPart()) { UnitUtility.defend(board) },
//                    UtilityTask(Fallback(
//
//                            backUp onlyIf OnCooldownWithBetterPosition,
//                            createAttackPart(),
//                            MoveToEnemyBase())) { 0.5 })
            , board
    )

    fun hasBehaviorFor(unit: PlayerUnit) = unitBehavior.containsKey(unit)

    fun createTreeFor(unit: PlayerUnit): BTree<*> {
        val behavior = when {
            (unit is Worker<*>) -> {
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
