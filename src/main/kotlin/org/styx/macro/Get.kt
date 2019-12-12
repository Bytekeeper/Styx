package org.styx.macro

import bwapi.Race
import bwapi.UnitType
import org.bk.ass.manage.GMS
import org.styx.*
import org.styx.Styx.buildPlan
import org.styx.Styx.resources
import org.styx.Styx.units
import java.util.*
import kotlin.math.max
import kotlin.math.min

class Get(private val amountProvider: () -> Int,
          val type: UnitType) : MemoLeaf() {
    private val children = ArrayDeque<SimpleNode>()

    constructor(amount: Int, type: UnitType) : this({ amount }, type)

    override fun tick(): NodeStatus {
        val amount = amountProvider()
        val completedUnitsAmount = units.myCompleted(type).size
        val lostUnitsOnPlan = if (type.isWorker && type.race == Race.Zerg) buildPlan.plannedUnits.count { it.type.isBuilding } else 0
        val missingOrIncomplete = max(0, amount - completedUnitsAmount + lostUnitsOnPlan)
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

        val availableBuilders = resources.availableUnits.count { type.whatBuilds().first == it.unitType && it.buildType == UnitType.None }
        val builders =
                availableBuilders +
                        determineFutureBuildersToBlockNow()
        val expectedChildCount =
                min(builders, (remaining + pendingUnitsFactor / 2) / pendingUnitsFactor)
        if (expectedChildCount == 0)
            return NodeStatus.RUNNING
        repeat(children.size - expectedChildCount) {
            children.removeFirst()
        }
        children.removeIf {
            it() != NodeStatus.RUNNING
        }
        repeat(expectedChildCount - children.size) {
            val newNode: SimpleNode = if (type.isBuilding) StartBuild(type) else StartTrain(type)
            children += newNode
            newNode()
        }
        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        children.clear()
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
                                !(Styx.resources.availableGMS + Styx.economy.estimatedAdditionalGMSIn(frames)).greaterOrEqual(estimatedCost)
                            }.count()
                } else 0
        return predictedAdditionalBuilders
    }

    override fun toString(): String = "Get ${amountProvider()} ${type.shortName()}"
}