package org.fttbot.behavior

import org.fttbot.*
import org.fttbot.estimation.MAX_FRAMES_TO_ATTACK
import org.fttbot.estimation.SimUnit
import org.fttbot.info.*
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit


class SelectRetreatAttackTarget : UnitLT() {
    override fun internalTick(board: BBUnit): NodeStatus {
        val unit = board.unit as MobileUnit
        if (unit !is Armed) return NodeStatus.FAILED
        if (!unit.canMoveWithoutBreakingAttack) return NodeStatus.SUCCEEDED
        if (unit.canBeAttacked()) return NodeStatus.FAILED
        if (unit.groundWeapon.onCooldown || unit.airWeapon.onCooldown) return NodeStatus.FAILED
        val enemyCluster = Cluster.enemyClusters.minBy { unit.getDistance(it.position) }
        if (enemyCluster == null) return NodeStatus.FAILED

        val closestEnemyCombatDistance =
                ClusterUnitInfo.getInfo(enemyCluster)
                        .unitsInArea.filter { it is Armed && it.getWeaponAgainst(unit).type() != WeaponType.None }
                        .map { it.getDistance(unit) - (it as Armed).getWeaponAgainst(unit).type().maxRange() }
                        .min() ?: Int.MAX_VALUE
        val enemyCandidates = enemyCluster.units
                .filter { closestEnemyCombatDistance > it.getDistance(unit) - unit.getWeaponAgainst(it).type().maxRange() }
        val simUnit = SimUnit.of(unit)
        val bestEnemy = enemyCandidates.minWith(Comparator { a, b ->
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
        if (unit !is Armed) return NodeStatus.FAILED
        if (!unit.canMoveWithoutBreakingAttack) return NodeStatus.SUCCEEDED
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
        if (!unit.canMoveWithoutBreakingAttack) return NodeStatus.RUNNING
        val target = board.target
        if (unit.groundWeapon.onCooldown || unit.airWeapon.onCooldown) return NodeStatus.SUCCEEDED
        if (target != unit.targetUnit) {
            if (target == null || !unit.attack(target)) return NodeStatus.FAILED
            board.nextOrderFrame = FTTBot.frameCount + FTTBot.latency_frames + unit.stopFrames
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
                    val weaponType = if (it is Armed) it.getWeaponAgainst(unit).type() else UnitType.Terran_Marine.groundWeapon()
                    val simEnemy = SimUnit.of(it as PlayerUnit)
                    val dpf = simEnemy.damagePerFrameTo(simUnit)
                    (unit.position - it.position!!).toVector().setLength(weaponType.maxRange() + 32f).add(it.position!!.toVector()).scl(dpf.toFloat()) to dpf
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
        if (UnitUtility.defend(board) > 0.7)
            return NodeStatus.FAILED
        if (probability < 0.55 && unit.canBeAttacked(64)) return NodeStatus.SUCCEEDED
        return NodeStatus.FAILED
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
            board.nextOrderFrame = FTTBot.frameCount + FTTBot.latency_frames
        }
        return NodeStatus.RUNNING
    }
}

// When attacking: Should this unit fall back a little?
object OnCooldownWithBetterPosition : UnitLT() {

    override fun internalTick(board: BBUnit): NodeStatus {
        if (board.unit is Armed && !board.unit.canMoveWithoutBreakingAttack) return NodeStatus.FAILED
        if (UnitUtility.fallback(board) > 0.3)
            return NodeStatus.SUCCEEDED
        return NodeStatus.FAILED
    }
}
