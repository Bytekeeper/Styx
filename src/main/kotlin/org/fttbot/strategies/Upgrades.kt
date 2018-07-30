package org.fttbot.strategies

import org.fttbot.FTTBot
import org.fttbot.NoFail
import org.fttbot.Utility
import org.fttbot.info.UnitQuery
import org.fttbot.info.isMelee
import org.fttbot.task.Production
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import kotlin.math.min

object Upgrades {
    fun uFlyerArmorUpgrade() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is AirAttacker } / (20.0 + 15 * FTTBot.self.getUpgradeLevel(UpgradeType.Zerg_Flyer_Carapace))) }, Production.upgrade(UpgradeType.Zerg_Flyer_Carapace)))

    fun uUpgradeFlyerAttacks() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is AirAttacker } / (15.0 + 15 * FTTBot.self.getUpgradeLevel(UpgradeType.Zerg_Flyer_Attacks))) }, Production.upgrade(UpgradeType.Zerg_Flyer_Attacks)))

    fun uChitinousPlatingUpgrade() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Ultralisk } / 12.0) }, Production.upgrade(UpgradeType.Chitinous_Plating)))

    fun uAnabolicSynthesisUpgrade() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Ultralisk } / 10.0) }, Production.upgrade(UpgradeType.Anabolic_Synthesis)))

    fun uAdrenalGlandsUpgrade() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Zergling } / 50.0) }, Production.upgrade(UpgradeType.Adrenal_Glands)))

    fun uMetabolicBoost() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Zergling } / 20.0) }, Production.upgrade(UpgradeType.Metabolic_Boost)))

    fun uGroundArmorUpgrade() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is GroundAttacker } / (80.0 + 20 * FTTBot.self.getUpgradeLevel(UpgradeType.Zerg_Carapace))) }, Production.upgrade(UpgradeType.Zerg_Carapace)))

    fun uMeleeAttacksUpgrade() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is GroundAttacker && it.groundWeapon.isMelee() } / (60.0 + 20 * FTTBot.self.getUpgradeLevel(UpgradeType.Zerg_Melee_Attacks))) }, Production.upgrade(UpgradeType.Zerg_Melee_Attacks)))

    fun uMissileAttacks() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Lurker || it is Hydralisk } / (10.0 + 20 * FTTBot.self.getUpgradeLevel(UpgradeType.Zerg_Missile_Attacks))) }, Production.upgrade(UpgradeType.Zerg_Missile_Attacks)))

    fun uGrooveSpines() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Hydralisk } / (12.0 + 20 * FTTBot.self.getUpgradeLevel(UpgradeType.Grooved_Spines))) }, Production.upgrade(UpgradeType.Grooved_Spines)))

    fun uMuscularAugments() =
            NoFail(Utility({ min(1.0, UnitQuery.myUnits.count { it is Hydralisk } / (10.0 + 20 * FTTBot.self.getUpgradeLevel(UpgradeType.Muscular_Augments))) }, Production.upgrade(UpgradeType.Muscular_Augments)))

}