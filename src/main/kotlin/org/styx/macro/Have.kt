package org.styx.macro

import bwapi.TechType
import bwapi.UnitType
import org.bk.ass.bt.*
import org.styx.ResourceReservation
import org.styx.Styx

class GetStuffToTrainOrBuild(private val type: UnitType) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            if (type == UnitType.None || type == UnitType.Zerg_Larva)
                LambdaNode {
                    NodeStatus.SUCCESS
                } else
                Parallel(
                        HaveResearched(type.requiredTech()),
                        *type.requiredUnits()
                                .filter { (type, _) -> type != UnitType.Zerg_Larva }
                                .map { (type, amount) ->
                                    Get({ amount }, type)
                                }.toTypedArray(),
                        HaveGas(type.gasPrice())
                ).withName("Ensuring dependencies for $type")
}

class HaveGas(private val amount: Int) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Get({ if (ResourceReservation.gms.gas < amount && amount > 0) 1 else 0 }, Styx.self.race.refinery)

}

class GetStuffToResearch(private val type: TechType) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            if (type == TechType.None)
                LambdaNode { NodeStatus.SUCCESS }
            else
                Parallel(
                        GetStuffToTrainOrBuild(type.whatResearches()),
                        HaveGas(type.gasPrice())
                )

}

class HaveResearched(private val type: TechType) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            if (type == TechType.None)
                LambdaNode { NodeStatus.SUCCESS }
            else
                Parallel(
                        GetStuffToResearch(type),
                        Research(type)
                )
}