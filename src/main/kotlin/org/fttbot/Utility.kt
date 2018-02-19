package org.fttbot

import com.badlogic.gdx.math.MathUtils.clamp
import org.fttbot.behavior.*
import org.fttbot.estimation.SimUnit
import org.fttbot.info.*
import org.openbw.bwapi4j.unit.*
import java.lang.Math.pow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class UtilitySelector<E>(vararg val children: UtilityTask<E>) : Node<E> {
    override fun tick(board: E): NodeStatus {
        val processingOrder = children.sortedByDescending { it.utility(board) }
        processingOrder.forEach {
            val util = it.utility(board)
            if (util == 0.0)
                return NodeStatus.FAILED
            val result = it.tick(board)
            if (result != NodeStatus.FAILED) return result
        }
        return NodeStatus.FAILED

    }
}

class UtilityTask<E>(val wrappedTask: Node<E>, val utility: (E) -> Double) : Node<E> {
    override fun tick(board: E): NodeStatus = wrappedTask.tick(board)
}

fun expI(range: Double, x: Double, k: Double) = min(1.0, pow(x, k) / pow(range, k))

object StrategyUtility {
    fun needMobileDetection() : Double {
        if (!EnemyState.hasInvisibleUnits) return 0.0
        return min(1.0, UnitQuery.myUnits.count { it is Armed } /
                (UnitQuery.myUnits.count { it is ComsatStation || it is MobileUnit && it.isDetector } * 40.0 + 10))
    }
}

object BuildUtility {
    fun supplyUtilisation(): Double = fastsig((FTTBot.self.supplyUsed() - FTTBot.self.supplyTotal()).toDouble()) / 2 + 0.5
    fun buildableSupplyRemaining(): Double = fastsig(400.0 - FTTBot.self.supplyTotal())
    fun buildSupply(): Double =  fastsig(supplyUtilisation() * buildableSupplyRemaining() * 10)
}

object UnitUtility {
    val caution = 0.3

    fun fallback(board: BBUnit): Double {
        val unit = board.unit
        if (unit !is Armed) return 0.0
        // Not engaged or shooting in this very moment? Don't fall back
        if (!unit.groundWeapon.onCooldown && !unit.airWeapon.onCooldown) return 0.0
        // Melee? Don't fall back, but maybe retreat
        if (unit.groundWeapon.onCooldown && unit.groundWeapon.isMelee()) return 0.0
        return min(1.0, unit.potentialAttackers().count {
            it is Armed && it.canAttack(unit, if (it is MobileUnit) (it.topSpeed() * max(unit.groundWeapon.cooldown(), unit.airWeapon.cooldown())).toInt() else 32)
                    && it.getWeaponAgainst(unit).type().maxRange() + 16 < unit.getWeaponAgainst(it).type().maxRange()
        } / 2.0 + nearDeath(unit))
    }

    fun cooldown(board: BBUnit): Double {
        val unit = board.unit
        if (unit !is Armed) return 1.0
        if (!unit.groundWeapon.onCooldown || !unit.airWeapon.onCooldown) return 0.0
        return 1.0 - max(unit.groundWeapon.cooldown(), unit.airWeapon.cooldown()) / (max(unit.groundWeapon.maxCooldown(), unit.airWeapon.maxCooldown()).toDouble())
    }

    fun health(unit: PlayerUnit) = unit.hitPoints / unit.maxHitPoints().toDouble()

    fun nearDeath(unit: PlayerUnit) = sqrt(1 - health(unit))

    fun attack(board: BBUnit): Double {
        val unit = board.unit as? Armed ?: return 0.0
        val simUnit = SimUnit.of(board.unit)
        val potentialTargets = UnitQuery.enemyUnits.inRadius(board.unit, max(unit.groundWeapon.maxRange(), unit.airWeapon.maxRange()))
        board.utility.attack = fastsig(potentialTargets.map { simUnit.damagePerFrameTo(SimUnit.of(it)) }.max()
                ?: return 0.0)
        return board.utility.attack
    }

    fun collateralThreat(board: BBUnit): Double {
        val unit = board.unit
        val simUnit = SimUnit.of(unit)
        board.utility.collateralThreat = fastsig(UnitQuery.enemyUnits.inRadius(unit, 300).filter { it is Armed }.map { SimUnit.of(it).damagePerFrameTo(simUnit) }.sum())
        return board.utility.collateralThreat
    }

    fun immediateThreat(unit: PlayerUnit): Double {
        val simUnit = SimUnit.of(unit)
        unit.board.utility.immediateThreat = fastsig(UnitQuery.enemyUnits
                .inRadius(unit, 300)
                .filter { it is Armed && it.canAttack(unit, (it.topSpeed() * 3).toInt()) }
                .map { SimUnit.of(it).damagePerFrameTo(simUnit) }
                .sum())
        return unit.board.utility.immediateThreat
    }

    fun runaway(board: BBUnit): Double {
        val unit = board.unit
        board.utility.runaway = fastsig(collateralThreat(board) + immediateThreat(unit) - defend(board) - force(board) - health(unit)) / 2 + 0.5
        return board.utility.runaway
    }

    fun danger(board: BBUnit): Double = clamp(caution * collateralThreat(board) / (0.1 + force(board)), 0.0, 1.0)

    fun defend(board: BBUnit): Double {
        val unit = board.unit
        board.utility.defend = fastsig(UnitQuery.myUnits.inRadius(unit, 300).map { worth(it) }.sum())
        return board.utility.defend
    }

    fun force(board: BBUnit): Double {
        val unit = board.unit
        val result = min(1.0, expI(10.0, UnitQuery.unitsInRadius(unit.position, 300)
                .count { it is Armed && it is PlayerUnit && it.isCompleted && it.isMyUnit && (it !is Worker || board.goal is Attacking || board.goal is Defending) }.toDouble(), 3.0))
        board.utility.force = result
        return result
    }

    fun worth(unit: PlayerUnit): Double {
        val result = 1.0
        if (unit.userData != null) unit.board.utility.worth = result
        return result
    }

    fun construct(board: BBUnit): Double {
        val result = if (board.goal !is Construction) 0.0 else
            1 - collateralThreat(board)
        board.utility.construct = result
        return result
    }

    fun scout(board: BBUnit): Double = if (board.goal is Scouting) 1.0 else 0.0

    fun gather(board: BBUnit): Double = if (board.goal is Gathering) 1.0 else 0.0
}