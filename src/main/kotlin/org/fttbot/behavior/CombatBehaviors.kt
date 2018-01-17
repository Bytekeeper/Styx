package org.fttbot.behavior

import com.badlogic.gdx.math.Vector2
import org.fttbot.*
import org.fttbot.decision.Utilities
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.MAX_FRAMES_TO_ATTACK
import org.fttbot.estimation.SimUnit
import org.fttbot.info.*
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.Color
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit


class SelectRetreatAttackTarget : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit
        if (unit !is Armed || unit.groundWeapon.onCooldown || unit.airWeapon.onCooldown) return NodeStatus.FAILED
        if (unit.canBeAttacked()) return NodeStatus.FAILED
        val safety = (unit.topSpeed() * MAX_FRAMES_TO_ATTACK / 8).toInt()
        val enemiesInArea = UnitQuery.unitsInRadius(unit.position, 300)
                .filter { it is PlayerUnit && it.isEnemyUnit && unit.canAttack(it, safety) }
                .map { it as PlayerUnit }
        val simUnit = SimUnit.of(unit)
        val bestEnemy = enemiesInArea.minWith(Comparator { a, b ->
            val damageCmp = simUnit.directDamage(SimUnit.of(b)).compareTo(simUnit.directDamage(SimUnit.of(a)))
            if (damageCmp != 0) damageCmp
            else a.hitPoints - b.hitPoints
        }) ?: return NodeStatus.FAILED
        board.target = bestEnemy
        return NodeStatus.SUCCEEDED
    }
}

class SelectBestAttackTarget : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit
        if (unit !is Armed || unit.groundWeapon.onCooldown && unit.airWeapon.onCooldown) return NodeStatus.FAILED
        val simUnit = SimUnit.of(unit)
        val enemiesInArea = UnitQuery.unitsInRadius(unit.position, 300)
                .filter { it is PlayerUnit && it.isEnemyUnit }
                .map { it as PlayerUnit }
        val safety = if (unit is MobileUnit) (unit.topSpeed() * MAX_FRAMES_TO_ATTACK / 2).toInt() else 0
        val bestEnemy = enemiesInArea.minWith(Comparator { a, b ->
            if (disregard(a) != disregard(b)) {
                if (disregard(b)) -1 else 1
            } else if (a.canAttack(unit) != b.canAttack(unit)) {
                if (a.canAttack(unit)) -1 else 1
            } else if (unit.canAttack(a, safety) != unit.canAttack(b, safety)) {
                if (unit.canAttack(a, safety)) -1 else 1
            } else {
                val damageA = simUnit.damagePerFrameTo(SimUnit.of(a))
                if (damageA == 0.0) return@Comparator 1
                val damageB = simUnit.damagePerFrameTo(SimUnit.of(b))
                if (damageB == 0.0) return@Comparator -1
                val valueCompare = (a.hitPoints / damageA).compareTo(b.hitPoints / damageB)
                if (valueCompare != 0) valueCompare
                else if (board.target == a) -1
                else if (board.target == b) 1
                else 0
            }
        }) ?: return NodeStatus.FAILED
        if (unit.getWeaponAgainst(bestEnemy).type() == WeaponType.None) return NodeStatus.FAILED
        if (board.target != bestEnemy && board.target != null) {
            LOG.finest("$unit is switching from ${board.target} to $bestEnemy");
        }
        board.target = bestEnemy
        return NodeStatus.SUCCEEDED
    }

    private fun disregard(unit: Unit) = unit.isA(UnitType.Zerg_Larva) ||
            unit.isA(UnitType.Zerg_Egg)
}

object Attack : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit
        if (unit !is Armed) return NodeStatus.FAILED
        val target = board.target
        if ((unit.groundWeapon.cooldown() > 0 || unit.airWeapon.cooldown() > 0) && unit.canMoveWithoutBreakingAttack) return NodeStatus.SUCCEEDED
        if (target != unit.targetUnit) {
            if (!unit.attack(target)) return NodeStatus.FAILED
        }
        return NodeStatus.RUNNING
    }
}

class FindGoodAttackPosition : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit
        val target = board.target ?: return NodeStatus.FAILED
        if (unit !is Armed) return NodeStatus.FAILED
        val simUnit = SimUnit.of(unit)
        val relevantEnemyPositions = (UnitQuery.unitsInRadius(unit.position, 300)
                + unit.getUnitsInRadius(300, EnemyState.seenUnits))
                .filter { it is PlayerUnit && it.isEnemyUnit && it.canAttack(unit, 32) }
                .map {
                    val wpn = (it as Armed).getWeaponAgainst(unit)
                    val simEnemy = SimUnit.of(it as PlayerUnit)
                    val dpf = simEnemy.damagePerFrameTo(simUnit)
                    (unit.position - it.position!!).toVector().setLength(wpn.type().maxRange() + 32f).add(it.position!!.toVector()).scl(dpf.toFloat()) to dpf
                }
        if (relevantEnemyPositions.isEmpty()) return NodeStatus.FAILED
        val aggPosition = relevantEnemyPositions
                .reduce { (pa, da), (pb, db) -> (pa.add(pb)) to (da + db) }
        val maxAvoidPosition = aggPosition.first.scl(1 / aggPosition.second.toFloat())
        val targetPos = target.position?.toVector()
        board.moveTarget = maxAvoidPosition.sub(targetPos).setLength(unit.getWeaponAgainst(target).type().maxRange().toFloat()).add(targetPos).toPosition()
        return NodeStatus.SUCCEEDED
    }
}

class UnfavorableSituation : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit
        val myCluster = Cluster.mobileCombatUnits.firstOrNull { it.units.contains(unit) } ?: return NodeStatus.FAILED
        val probability = ClusterUnitInfo.getInfo(myCluster).combatEval
        if (Utilities.defend(board) > 0.7)
            return NodeStatus.FAILED
        if (probability < 0.55 && unit.canBeAttacked(192)) return NodeStatus.SUCCEEDED
        return NodeStatus.FAILED
    }
}

class Retreat : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as MobileUnit

        if (!unit.isMoving || unit.targetPosition.toTilePosition().getDistance(FTTBot.self.startLocation) > 2) {
            unit.move(FTTBot.self.startLocation.toPosition())
        }
        return NodeStatus.RUNNING
    }
}

class MoveToEnemyBase : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val enemyBases = EnemyState.enemyBases.sortedBy { it.units.count { it is Armed } }
        val bestEnemyBase = enemyBases.firstOrNull { it.units.any { it is Base } } ?: enemyBases.firstOrNull()
        val target = bestEnemyBase?.position ?: return NodeStatus.FAILED
        val unit = board.unit as MobileUnit
        if (!unit.isMoving || unit.targetPosition != target) {
            unit.move(target)
        }
        return NodeStatus.RUNNING
    }
}

// When attacking: Should this unit fall back a little?
class OnCooldownWithBetterPosition : UnitLT() {

    override fun internalTick(board: BBUnit): NodeStatus {
        if (Utilities.fallback(board) > 0.3)
            return NodeStatus.SUCCEEDED
        return NodeStatus.FAILED
    }
}

object FallBack : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as MobileUnit
        val repulse = Potential.threatsRepulsion(unit)
        if (repulse == Vector2.Zero) return NodeStatus.FAILED
        val targetPosition = repulse.add(Potential.collisionRepulsion(unit))
                .setLength(2f * (unit.topSpeed() * unit.groundWeapon.cooldown()).toFloat())
                .add(unit.position.toVector())
                .toPosition()
        if (FTTBot.game.bwMap.isWalkable(targetPosition.toWalkPosition()) && unit.targetPosition.getDistance(targetPosition) > 16) {
            if (!unit.move(targetPosition)) return NodeStatus.FAILED
        }
        if (unit.position.getDistance(targetPosition) < 16) return NodeStatus.SUCCEEDED
        return NodeStatus.RUNNING
    }

}