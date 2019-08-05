package org.styx

import bwapi.UnitType
import bwapi.WeaponType

object TargetEvaluator {
    fun bestTarget(attacker: SUnit, targets: Collection<SUnit>): SUnit? {
        return targets
                .filter {
                    it.detected &&
                            attacker.weaponAgainst(it) != WeaponType.None &&
                            it.unitType != UnitType.Zerg_Larva
                }
                .maxBy {
                    (if (it.unitType.canAttack()) 1.0 else 0.0) +
                            (if (it.unitType.isBuilding) 0.5 else 1.0) +
                            (if (it.unitType == UnitType.Zerg_Egg || it.unitType == UnitType.Zerg_Lurker_Egg) 0.0 else 1.0) -
                            attacker.distanceTo(it) / 1000.0
                }
    }
}