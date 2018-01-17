package org.fttbot.decision

import com.badlogic.gdx.math.MathUtils.clamp
import org.fttbot.Node
import org.fttbot.NodeStatus
import org.fttbot.behavior.BBUnit
import org.fttbot.behavior.Construction
import org.fttbot.behavior.Gathering
import org.fttbot.behavior.Scouting
import org.fttbot.info.*
import org.openbw.bwapi4j.unit.Armed
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Unit
import org.openbw.bwapi4j.unit.Worker
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

fun expF(range: Double, x: Double, k: Double) = max(0.0, pow(range - x, k) / pow(range, k))
fun expI(range: Double, x: Double, k: Double) = min(1.0, pow(x, k) / pow(range, k))

object Utilities {
    val caution = 0.5

    fun fallback(board: BBUnit): Double {
        val unit = board.unit as MobileUnit
        // Not engaged or shooting in this very moment? Don't fall back
        if (!unit.groundWeapon.onCooldown || !unit.airWeapon.onCooldown) return 0.0
        // Melee? Don't fall back, but maybe retreat
        if (unit.groundWeapon.onCooldown && unit.groundWeapon.isMelee()) return 0.0
        return min(1.0, unit.potentialAttackers().count {
            it is Armed && it.canAttack(unit, if (it is MobileUnit) (it.topSpeed() * max(unit.groundWeapon.cooldown(), unit.airWeapon.cooldown())).toInt() else 32)
                    && it.getWeaponAgainst(unit).type().maxRange() + 16 < unit.getWeaponAgainst(it).type().maxRange()
        } / 2.0 + nearDeath(unit))
    }

    fun health(unit : PlayerUnit) = unit.hitPoints / unit.maxHitPoints().toDouble()

    fun nearDeath(unit: PlayerUnit) = sqrt(1 - health(unit))

    fun attack(board: BBUnit): Double {
        val unit = board.unit as Armed
        val result = max(with(unit.groundWeapon) { (type().damageCooldown() - cooldown()) / cooldown().toDouble() },
                with(unit.airWeapon) { (type().damageCooldown() - cooldown()) / cooldown().toDouble() })
        board.utility.attack = result
        return result
    }

    fun runaway(board: BBUnit): Double {
        val unit = board.unit
        return min(1.0, danger(board) * caution * (if (unit is Worker<*>) 2.0 else 1.0))
    }

    fun danger(board: BBUnit): Double = clamp(caution * threat(board) / (0.1 + force(board)), 0.0, 1.0)

    fun defend(board: BBUnit): Double {
        val result = min(1.0, 3 * danger(board) * value(board))
        board.utility.defend = result
        return result
    }

    fun force(board: BBUnit): Double {
        val unit = board.unit
        val result = min(1.0, expI(10.0, UnitQuery.unitsInRadius(unit.position, 300)
                .count { it is Armed && it is PlayerUnit && it.isCompleted && it.isMyUnit }.toDouble(), 3.0))
        board.utility.force = result
        return result
    }

    fun threat(board: BBUnit): Double {
        val unit = board.unit
        val result = expI(10.0, UnitQuery.unitsInRadius(unit.position, 300).count { it is Armed && it is PlayerUnit && it.isEnemyUnit }.toDouble(), 0.8)
        board.utility.threat = result
        return result
    }

    fun value(board: BBUnit): Double {
        val unit = board.unit
        val result = expI(10.0, UnitQuery.unitsInRadius(unit.position, 300).filterIsInstance(PlayerUnit::class.java).filter { it.isMyUnit }
                .count().toDouble(), 3.0)
        board.utility.value = result
        return result
    }

    fun construct(board: BBUnit): Double {
        val result = if (board.goal !is Construction) 0.0 else
            1 - threat(board)
        board.utility.construct = result
        return result
    }

    fun scout(board: BBUnit): Double = if (board.goal is Scouting) 1.0 else 0.0

    fun gather(board: BBUnit): Double = if (board.goal is Gathering) 1.0 else 0.0
}