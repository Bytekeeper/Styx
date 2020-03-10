package org.styx.macro

import bwapi.UnitType
import org.bk.ass.bt.BehaviorTree
import org.bk.ass.bt.Parallel
import org.bk.ass.bt.TreeNode
import org.styx.Styx

class EnsureDependenciesFor(private val type: UnitType) : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Parallel(
                    *type.requiredUnits()
                            .filter { (type, _) -> type != UnitType.Zerg_Larva }
                            .map { (type, amount) ->
                                Get({ amount }, type)
                            }.toTypedArray(),
                    Get({ if (type.gasPrice() > 0) 1 else 0 }, Styx.self.race.refinery))
                    .withName("Ensuring dependencies for $type")
}
