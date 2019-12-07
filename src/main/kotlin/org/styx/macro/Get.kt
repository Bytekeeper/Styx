package org.styx.macro

import bwapi.UnitType
import org.bk.ass.manage.GMS
import org.styx.*
import org.styx.Styx.units
import java.util.*
import kotlin.math.max
import kotlin.math.min

class Get(private val amountProvider: () -> Int,
          val type: UnitType) : MemoLeaf() {
    private val children = ArrayDeque<BTNode>()

    override fun tick(): NodeStatus {
        val amount = amountProvider()
        val completedUnitsAmount = units.myCompleted(type).size
        val missingOrIncomplete = max(0, amount - completedUnitsAmount)
        if (missingOrIncomplete == 0) {
            children.clear()
            return NodeStatus.DONE
        }
        val pendingUnitsFactor = if (type.isTwoUnitsInOneEgg) 2 else 1
        val pendingUnits = units.myPending.count { it.unitType == type }
        val remaining = max(0, missingOrIncomplete - (pendingUnits + Styx.buildPlan.plannedUnits.count { it.type == type }) * pendingUnitsFactor)
        if (remaining == 0) {
            return NodeStatus.RUNNING
        }

        val builders = units.my(type.whatBuilds().first).count { it.buildType == UnitType.None } + determineFutureBuildersToBlockNow()
        val expectedChildCount =
                min(builders, (remaining + pendingUnitsFactor / 2) / pendingUnitsFactor)
        if (expectedChildCount == 0)
            return NodeStatus.RUNNING
        repeat(children.size - expectedChildCount) {
            children.removeFirst()
        }
        children.removeIf {
            it.perform() != NodeStatus.RUNNING
        }
        repeat(expectedChildCount - children.size) {
            val newNode = if (type.isBuilding) StartBuild(type) else StartTrain(type)
            children += newNode
            newNode.perform()
        }
        return NodeStatus.RUNNING
    }

    // Do we have enough money when the builder arrives? If not, we should lock resources etc.
    private fun determineFutureBuildersToBlockNow(): Int {
        val costPerUnit = GMS.unitCost(type)
        val predictedAdditionalBuilders =
                if (type.whatBuilds().first == UnitType.Zerg_Larva) {
                    var estimatedCost = GMS(0, 0, 0)
                    Styx.units.myResourceDepots.filter { it.remainingTrainTime > 0 }
                            .sortedBy { it.remainingTrainTime }
                            .asSequence()
                            .map { it.remainingTrainTime }
                            .takeWhile { frames ->
                                estimatedCost += costPerUnit
                                !(Styx.resources.availableGMS + Styx.economy.estimatedAdditionalGMSIn(frames)).greaterOrEqual(estimatedCost)
                            }.count()
                } else 0
        return predictedAdditionalBuilders
    }

    override fun toString(): String = "Get ${amountProvider()} ${type.shortName()}"
}