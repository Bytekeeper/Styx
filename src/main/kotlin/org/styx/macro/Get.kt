package org.styx.macro

import bwapi.UnitType
import org.bk.ass.bt.NodeStatus
import org.bk.ass.bt.TreeNode
import org.bk.ass.manage.GMS
import org.styx.*
import org.styx.Styx.buildPlan
import org.styx.Styx.units
import org.styx.global.Unrealized
import java.util.*
import kotlin.math.max
import kotlin.math.min

class Get(private val amountProvider: () -> Int,
          private val type: UnitType,
          private val limitByAvailableBuilders: Boolean = false) : MemoLeaf() {
    private val children = ArrayDeque<TreeNode>()

    constructor(amount: Int, type: UnitType, limitByAvailableBuilders: Boolean = false) : this({ amount }, type, limitByAvailableBuilders)

    override fun tick(): NodeStatus {
        val amount = amountProvider()
        val completedUnitsAmount = units.myCompleted(type).size
        val lostUnitsOnPlan = buildPlan.plannedUnits.count { it.consumedUnit == type }
        val missingOrIncomplete = max(0, amount - completedUnitsAmount + lostUnitsOnPlan)
        if (missingOrIncomplete == 0) {
            children.clear()
            return NodeStatus.SUCCESS
        }
        val pendingUnitsFactor = if (type.isTwoUnitsInOneEgg) 2 else 1
        val remaining = max(0, missingOrIncomplete - units.myProjectedNew(type) * pendingUnitsFactor)
        if (remaining == 0) {
            return NodeStatus.RUNNING
        }

        val availableBuilders = UnitReservation.availableItems.count { type.whatBuilds().first == it.unitType && it.buildType == UnitType.None }
        val builders = availableBuilders + determineFutureBuildersToBlockNow()
        val expectedChildCount =
                if (limitByAvailableBuilders)
                    min(builders, (remaining + pendingUnitsFactor / 2) / pendingUnitsFactor)
                else
                    (remaining + pendingUnitsFactor / 2) / pendingUnitsFactor
        buildPlan.unrealized += Unrealized(type,  remaining - expectedChildCount)
        if (expectedChildCount == 0)
            return NodeStatus.RUNNING
        repeat(children.size - expectedChildCount) {
            children.removeFirst()
        }
        children.removeIf {
            it.exec()
            it.status != NodeStatus.RUNNING
        }
        repeat(expectedChildCount - children.size) {
            val newNode = when {
                type.isBuilding && type.whatBuilds().first.isWorker -> StartBuild(type)
                type.isBuilding -> Morph(type)
                type.whatBuilds().first.isBuilding -> StartTrain(type)
                else -> Morph(type)
            }
            children += newNode
            newNode.init()
            newNode.exec()
        }
        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        children.clear()
    }

    override fun abort() {
        super.abort()
        children.filter { it.status === NodeStatus.RUNNING }
                .forEach { it.abort() }
    }

    // Do we have enough money when the builder arrives? If not, we should lock resources etc.
    private fun determineFutureBuildersToBlockNow(): Int {
        val costPerUnit = GMS.unitCost(type)
        val predictedAdditionalBuilders =
                if (type.whatBuilds().first == UnitType.Zerg_Larva) {
                    var estimatedCost = GMS(0, 0, 0)
                    units.myResourceDepots.filter { it.remainingTrainTime > 0 }
                            .sortedBy { it.remainingTrainTime }
                            .asSequence()
                            .map { it.remainingTrainTime }
                            .takeWhile { frames ->
                                estimatedCost += costPerUnit
                                !(ResourceReservation.gms + Styx.economy.estimatedAdditionalGMSIn(frames)).canAfford(estimatedCost)
                            }.count()
                } else 0
        return predictedAdditionalBuilders
    }

    override fun toString(): String = "Get ${amountProvider()} ${type.shortName()}"
}