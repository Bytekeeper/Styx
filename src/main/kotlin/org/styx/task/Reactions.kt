package org.styx.task

import bwapi.UnitType
import bwapi.WeaponType
import org.bk.ass.bt.*
import org.styx.Styx
import org.styx.Styx.self
import org.styx.Styx.units
import org.styx.canUpgrade
import org.styx.macro.Get
import org.styx.macro.Upgrade
import org.styx.overlordSpeed
import kotlin.math.max

class ReactByUpgradingOverlordSpeed : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Repeat(Repeat.Policy.SELECTOR,
                    Sequence(
                            Condition {
                                units.enemy.any { it.unitType.hasPermanentCloak() || it.unitType.isCloakable || it.unitType.isFlyer && it.unitType.airWeapon() != WeaponType.None }
                                        && !self.canUpgrade(overlordSpeed)
                            },
                            Upgrade(overlordSpeed, 1)
                    )
            )
}

class ReactWithLings : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Repeat(Repeat.Policy.SELECTOR,
                    Get({ max(0, units.enemy.sumBy { if (it.unitType == UnitType.Zerg_Zergling) 1 else if (it.unitType == UnitType.Protoss_Zealot) 2 else 0 }) },
                            UnitType.Zerg_Zergling)
            )
}

class ReactByCancellingDyingBuildings : TreeNode() {
    override fun exec() {
        units.mine.filter { u ->
            u.unitType.isBuilding
                    && u.hitPoints < u.unitType.maxHitPoints() / 4
                    && u.remainingBuildTime > u.hitPoints / (u.engaged.sumByDouble { it.damagePerFrameVs(u) } + 0.001)
        }.forEach {
            it.cancelBuild()
        }
    }

}