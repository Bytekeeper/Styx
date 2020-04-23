package org.styx.task

import bwapi.Race
import bwapi.UnitType
import bwapi.WeaponType
import org.bk.ass.bt.*
import org.styx.ResourceReservation
import org.styx.Styx
import org.styx.Styx.game
import org.styx.Styx.self
import org.styx.Styx.units
import org.styx.canUpgrade
import org.styx.macro.Get
import org.styx.macro.Upgrade
import org.styx.overlordSpeed
import kotlin.math.ceil
import kotlin.math.max

object Reactions : Parallel(
        ReactByUpgradingOverlordSpeed,
        ReactWithSpores,
        ReactWithSunkens,
        ReactWithLings,
        ReactByCancellingDyingBuildings,
        ReactWithMutasForTerranFlyingBuildings
)

object ReactByUpgradingOverlordSpeed : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Repeat(Repeat.Policy.SELECTOR,
                    Sequence(
                            Condition {
                                units.enemy.any { it.unitType.hasPermanentCloak() || it.unitType.isCloakable || it.unitType.isFlyer && it.unitType.airWeapon() != WeaponType.None }
                                        && self.canUpgrade(overlordSpeed)
                            },
                            Upgrade(overlordSpeed, 1)
                    )
            )
}

object ReactWithSpores : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Repeat(Repeat.Policy.SEQUENCE,
                    Get({
                        ceil(
                                (units.enemy.count { it.flying && (it.unitType.groundWeapon() != WeaponType.None || it.unitType.airWeapon() != WeaponType.None) }
                                        - units.mine.count { it.unitType.airWeapon() != WeaponType.None })
                                        / 5.0).toInt()
                    }, UnitType.Zerg_Spore_Colony)
            )

}

object ReactWithSunkens : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Repeat(Repeat.Policy.SEQUENCE,
                    Get({
                        ceil(7.0 - Styx.balance.globalVsGroundEval.value * 18.0).toInt()
                    }, UnitType.Zerg_Sunken_Colony)
            )
}

object ReactWithLings : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Repeat(Repeat.Policy.SEQUENCE,
                    Succeeder(
                            Sequence(
                                    Condition {
                                        units.enemy.sumBy { if (it.unitType == UnitType.Zerg_Zergling) 1 else if (it.unitType == UnitType.Protoss_Zealot) 2 else 0 } <
                                                units.mine.sumBy { if (it.unitType == UnitType.Zerg_Zergling) 1 else if (it.unitType == UnitType.Protoss_Zealot) 2 else 0 }
                                    },
                                    ensureSupply(),
                                    Get({ max(0, units.enemy.sumBy { if (it.unitType == UnitType.Zerg_Zergling) 1 else if (it.unitType == UnitType.Protoss_Zealot) 2 else 0 }) },
                                            UnitType.Zerg_Zergling)
                            )
                    )
            )
}

object ReactByCancellingDyingBuildings : TreeNode() {
    override fun exec() {
        units.mine.filter { u ->
            u.unitType.isBuilding
                    && u.hitPoints < u.unitType.maxHitPoints() / 4
                    && (u.remainingBuildTime + 96) > u.hitPoints / (u.engaged.sumByDouble { it.damagePerFrameVs(u) } + 0.001)
        }.forEach {
            it.cancelBuild()
        }
    }
}


object Manners : TreeNode() {
    override fun init() {
        super.init()
        game.sendText("GL HF")
    }

    override fun close() {
        game.sendText("GG")
        super.close()
    }

    override fun exec() {
        if (self.supplyUsed() == 0
                && self.minerals() < 50
                && units.enemy.any { it.isCombatRelevant }
                && units.myWorkers.isEmpty()) {
            game.leaveGame()
        }
    }
}

object ReactWithMutasForTerranFlyingBuildings : BehaviorTree() {
    override fun getRoot(): TreeNode =
            Repeat(Repeat.Policy.SELECTOR,
                    Sequence(
                            Condition {
                                game.enemy().race == Race.Terran && units.mine.none {
                                    it.flying && it.unitType.airWeapon() != WeaponType.None
                                } && units.enemy.all { it.flying } && units.enemy.isNotEmpty()
                            },
                            Parallel(
                                    Get({ 8 }, UnitType.Zerg_Drone),
                                    pumpMutas()
                            )
                    )
            )

}