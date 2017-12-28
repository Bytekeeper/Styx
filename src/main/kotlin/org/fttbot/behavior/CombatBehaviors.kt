package org.fttbot.behavior

import bwapi.Color
import bwapi.Position
import com.badlogic.gdx.math.Intersector
import org.fttbot.*
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.EnemyModel
import org.fttbot.estimation.MAX_FRAMES_TO_ATTACK
import org.fttbot.estimation.SimUnit
import org.fttbot.import.FUnitType
import org.fttbot.layer.FUnit
import org.fttbot.layer.getWeaponAgainst
import org.fttbot.layer.isMelee


class SelectRetreatAttackTarget : UnitLT() {
    override fun start() {
    }

    override fun execute(): Status {
        val unit = board().unit
        if (unit.groundWeaponIsOnCooldown || unit.airWeaponIsOnCooldown) return Status.FAILED
        if (unit.canBeAttacked()) return Status.FAILED
        val safety = (unit.type.topSpeed * MAX_FRAMES_TO_ATTACK / 8).toInt()
        val enemiesInArea = FUnit.unitsInRadius(unit.position, 300)
                .filter { it.isEnemy && unit.canAttack(it, safety) }
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
        val enemiesInArea = FUnit.unitsInRadius(unit.position, 300).filter { it.isEnemy }
        val safety = if (unit.canMove) (unit.type.topSpeed * MAX_FRAMES_TO_ATTACK / 2).toInt() else 0
        val bestEnemy = enemiesInArea.minWith(Comparator { a, b ->
            if (disregard(a.type) != disregard(b.type)) {
                if (disregard(b.type)) -1 else 1
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

    private fun disregard(unitType: FUnitType) = when (unitType) {
        FUnitType.Zerg_Larva -> true
        FUnitType.Zerg_Egg -> true
        else -> false
    }
}

class Attack : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        val attacking = board().attacking ?: throw IllegalStateException()
        if (unit.isStartingAttack) return Status.RUNNING
        val target = attacking.target
        if (target.isAir && unit.airWeaponIsOnCooldown || !target.isAir && unit.groundWeaponIsOnCooldown) return Status.SUCCEEDED
        if (target != unit.target || !unit.isAttacking) {
            if (!unit.attack(target)) return Status.FAILED
        }
        return Status.RUNNING
    }
}

class FindGoodAttackPosition : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        val simUnit = SimUnit.of(unit)
        val relevantEnemyPositions = (FUnit.unitsInRadius(unit.position, 300)
                .filter { it.canAttack(unit, 32) && it.isEnemy } + EnemyModel.seenUnits.filter { it.position != null && it.distanceTo(unit) <= 300 })
                .map {
                    val wpn = it.type.getWeaponAgainst(unit)
                    val simEnemy = SimUnit.of(it)
                    val dpf = simEnemy.damagePerFrameTo(simUnit)
                    (unit.position - it.position!!).toVector().setLength(wpn.maxRange + 32f).add(it.position!!.toVector()).scl(dpf.toFloat()) to dpf
                }
        if (relevantEnemyPositions.isEmpty()) return Status.FAILED
        val aggPosition = relevantEnemyPositions
                .reduce { (pa, da), (pb, db) -> (pa.add(pb)) to (da + db) }
        val maxAvoidPosition = aggPosition.first.scl(1 / aggPosition.second.toFloat())
        val targetPos = board().attacking!!.target.position.toVector()
        board().moveTarget = maxAvoidPosition.sub(targetPos).setLength(unit.type.getWeaponAgainst(board().attacking!!.target).maxRange.toFloat()).add(targetPos).toPosition()
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
        val unit = board().unit
        if (unit.isInRefinery) return 0.5
        val unitsInArea = FUnit.unitsInRadius(unit.position, 600)
                .filter { it.isCompleted && it.canAttack }
        val center = unitsInArea.fold(Position(0, 0)) { a, u -> a + u.position } / unitsInArea.size
        val relevantUnits = unitsInArea.filter { it.distanceTo(center) < 300 }.toMutableSet()
        relevantUnits.add(unit)
        val enemyUnits = relevantUnits.filter { it.isEnemy }.map { SimUnit.of(it) }.toMutableList()
        EnemyModel.seenUnits.filter {
            (it.position?.getDistance(center) ?: Double.MAX_VALUE) < 300
                    && !FTTBot.game.isVisible(it.tilePosition)
        }.map { SimUnit.of(it) }
                .forEach { enemyUnits += it }
        FTTBot.game.drawCircleMap(center, 300, Color.Yellow)
        val probability = CombatEval.probabilityToWin(
                relevantUnits.filter { it.isPlayerOwned && (!it.isWorker || it.board.attacking != null) }.map { SimUnit.of(it) },
                enemyUnits)
        board().combatSuccessProbability = probability
        return probability
    }
}

class Retreat : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit

        if (!unit.isMoving || unit.targetPosition.toTilePosition().getDistance(FTTBot.self.startLocation) > 2) {
            unit.move(FTTBot.self.startLocation)
        }
        return Status.RUNNING
    }
}

class MoveToEnemyBase : UnitLT() {
    override fun execute(): Status {
        val target = EnemyModel.enemyBase ?: return Status.FAILED
        val unit = board().unit
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
        val unit = board().unit
        // Not engaged or shooting in this very moment? Don't fall back
        if (unit.isStartingAttack || !unit.groundWeaponIsOnCooldown && !unit.airWeaponIsOnCooldown) return Status.FAILED
        // Melee? Don't fall back, but maybe retreat
        if (unit.groundWeaponIsOnCooldown && unit.groundWeapon.isMelee()) return Status.FAILED
        unit.potentialAttackers().firstOrNull {
            it.canAttack(unit, 32)
                    && it.type.getWeaponAgainst(unit).maxRange + 32 < unit.type.getWeaponAgainst(it).maxRange
        } ?: return Status.FAILED
        return Status.SUCCEEDED
    }
}

class FallBack : UnitLT() {
    override fun execute(): Status {
        val unit = board().unit
        val relevantEnemyPositions = unit.potentialAttackers().map { it.position!! }
        val enemyCenter = relevantEnemyPositions.fold(Position(0, 0)) { a, b -> a + b } / relevantEnemyPositions.size
        val targetPosition = unit.position.toVector()
                .sub(enemyCenter.toVector())
                .setLength((unit.type.topSpeed * unit.groundWeaponCooldown).toFloat())
                .toPosition() + unit.position
        if (FTTBot.game.isWalkable(targetPosition.toWalkable()) && unit.targetPosition.getDistance(targetPosition) > 16) {
            if (!unit.move(targetPosition)) return Status.FAILED
        }
        if (unit.position.getDistance(targetPosition) < 16) return Status.SUCCEEDED
        return Status.RUNNING
    }

}