package org.fttbot.behavior

import org.fttbot.*
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.EnemyModel
import org.fttbot.estimation.MAX_FRAMES_TO_ATTACK
import org.fttbot.estimation.SimUnit
import org.fttbot.layer.*
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.Color
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Armed
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Unit


class SelectRetreatAttackTarget : UnitLT() {
    override fun start() {
    }

    override fun execute(): Status {
        val unit = board().unit
        if (unit !is Armed || unit.groundWeapon.onCooldown || unit.airWeapon.onCooldown) return Status.FAILED
        if (unit.canBeAttacked()) return Status.FAILED
        val safety = (unit.topSpeed() * MAX_FRAMES_TO_ATTACK / 8).toInt()
        val enemiesInArea = UnitQuery.unitsInRadius(unit.position, 300)
                .filter { it is PlayerUnit && it.isEnemyUnit && unit.canAttack(it, safety) }
                .map { it as PlayerUnit }
        val simUnit = SimUnit.of(unit)
        val bestEnemy = enemiesInArea.minWith(Comparator { a, b ->
            val damageCmp = simUnit.directDamage(SimUnit.of(b)).compareTo(simUnit.directDamage(SimUnit.of(a)))
            if (damageCmp != 0) damageCmp
            else a.hitPoints - b.hitPoints
        }) ?: return Status.FAILED
        board().attacking = Attacking(bestEnemy)
        return Status.SUCCEEDED
    }

}

class SelectBestAttackTarget : UnitLT() {
    override fun start() {
    }

    override fun execute(): Status {
        val unit = board().unit
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
                (a.hitPoints / damageA).compareTo(b.hitPoints / damageB)
            }
        }) ?: return Status.FAILED
        board().attacking = Attacking(bestEnemy)
        return Status.SUCCEEDED
    }

    private fun disregard(unit: Unit) = unit.isA(UnitType.Zerg_Larva) ||
            unit.isA(UnitType.Zerg_Egg)
}

class Attack : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        if (unit !is Armed) return Status.FAILED
        val attacking = board().attacking ?: throw IllegalStateException()
        if (unit.isStartingAttack) return Status.RUNNING
        val target = attacking.target
        if (target is PlayerUnit && (target.isFlyer && unit.airWeapon.onCooldown ||
                !target.isFlyer && unit.groundWeapon.onCooldown)) return Status.SUCCEEDED
        if (target != unit.targetUnit || !unit.isAttacking) {
            if (!unit.attack(target)) return Status.FAILED
        }
        return Status.RUNNING
    }
}

class FindGoodAttackPosition : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        if (unit !is Armed) return Status.FAILED
        val simUnit = SimUnit.of(unit)
        val relevantEnemyPositions = UnitQuery.unitsInRadius(unit.position, 300)
                .filter { it is PlayerUnit && it.isEnemyUnit && it.canAttack(unit, 32) }
                .map {
                    val wpn = (it as Armed).getWeaponAgainst(unit)
                    val simEnemy = SimUnit.of(it as PlayerUnit)
                    val dpf = simEnemy.damagePerFrameTo(simUnit)
                    (unit.position - it.position!!).toVector().setLength(wpn.type().maxRange() + 32f).add(it.position!!.toVector()).scl(dpf.toFloat()) to dpf
                }
        if (relevantEnemyPositions.isEmpty()) return Status.FAILED
        val aggPosition = relevantEnemyPositions
                .reduce { (pa, da), (pb, db) -> (pa.add(pb)) to (da + db) }
        val maxAvoidPosition = aggPosition.first.scl(1 / aggPosition.second.toFloat())
        val targetPos = board().attacking!!.target.position.toVector()
        board().moveTarget = maxAvoidPosition.sub(targetPos).setLength(unit.getWeaponAgainst(board().attacking!!.target).type().maxRange().toFloat()).add(targetPos).toPosition()
        return Status.SUCCEEDED
    }
}

class UnfavorableSituation : SituationEvaluator() {
    override fun execute(): Status {
        val probability = evaluate()
        val unit = board().unit
        if (probability < 0.55 && unit.canBeAttacked()) return Status.SUCCEEDED
        return Status.FAILED
    }
}

class EmergencySituation : SituationEvaluator() {
    override fun execute(): Status {
        val probability = evaluate()
        if (probability < 0.33) return Status.SUCCEEDED
        if (probability < 0.7 && board().attacking != null) return Status.SUCCEEDED
        board().attacking = null
        return Status.FAILED
    }
}

abstract class SituationEvaluator : UnitLT() {
    override fun start() {
    }

    protected fun evaluate(): Double {
        val unit = board().unit as MobileUnit
        val unitsInArea = UnitQuery.unitsInRadius(unit.position, 600)
                .filter { it is PlayerUnit && it is Armed && it.isCompleted }
                .map { it as PlayerUnit }
        val center = unitsInArea.fold(Position(0, 0)) { a, u -> a + u.position } / unitsInArea.size
        val relevantUnits = unitsInArea.filter { it.getDistance(center) < 300 }.toMutableSet()
        relevantUnits.add(unit)
        val enemyUnits = relevantUnits.filter { it.isEnemyUnit }.map { SimUnit.of(it) }.toMutableList()

        FTTBot.render.drawCircleMap(center, 300, Color.YELLOW)
        val probability = CombatEval.probabilityToWin(
                relevantUnits.filter { it.isMyUnit && (it !is MobileUnit || !it.isWorker || it.board.attacking != null) }.map { SimUnit.of(it) },
                enemyUnits)
        board().combatSuccessProbability = probability
        return probability
    }
}

class Retreat : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit as MobileUnit

        if (!unit.isMoving || unit.targetPosition.toTilePosition().getDistance(FTTBot.self.startLocation) > 2) {
            unit.move(FTTBot.self.startLocation.toPosition())
        }
        return Status.RUNNING
    }
}

class MoveToEnemyBase : UnitLT() {
    override fun execute(): Status {
        val target = EnemyModel.enemyBase ?: return Status.FAILED
        val unit = board().unit as MobileUnit
        if (!unit.isMoving || unit.targetPosition != target) {
            unit.move(target)
        }
        return Status.RUNNING
    }
}

// When attacking: Should this unit fall back a little?
class ShouldFallBack : UnitLT() {

    override fun start() {
    }

    override fun execute(): Status {
        val unit = board().unit as MobileUnit
        // Not engaged or shooting in this very moment? Don't fall back
        if (unit.isStartingAttack || !unit.groundWeapon.onCooldown && !unit.airWeapon.onCooldown) return Status.FAILED
        // Melee? Don't fall back, but maybe retreat
        if (unit.groundWeapon.onCooldown && unit.groundWeapon.isMelee()) return Status.FAILED
        unit.potentialAttackers().firstOrNull {
            it is Armed && it.canAttack(unit, 32)
                    && it.getWeaponAgainst(unit).type().maxRange() + 32 < unit.getWeaponAgainst(it).type().maxRange()
        } ?: return Status.FAILED
        return Status.SUCCEEDED
    }
}

class FallBack : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit as MobileUnit
        val relevantEnemyPositions = unit.potentialAttackers().map { it.position!! }
        val enemyCenter = relevantEnemyPositions.fold(Position(0, 0)) { a, b -> a + b } / relevantEnemyPositions.size
        val targetPosition = unit.position.toVector()
                .sub(enemyCenter.toVector())
                .setLength((unit.topSpeed() * unit.groundWeapon.cooldown()).toFloat())
                .toPosition() + unit.position
        if (FTTBot.game.bwMap.isWalkable(targetPosition.toWalkable()) && unit.targetPosition.getDistance(targetPosition) > 16) {
            if (!unit.move(targetPosition)) return Status.FAILED
        }
        if (unit.position.getDistance(targetPosition) < 16) return Status.SUCCEEDED
        return Status.RUNNING
    }

}