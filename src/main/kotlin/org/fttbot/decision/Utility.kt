package org.fttbot.decision

import com.badlogic.gdx.ai.btree.Decorator
import com.badlogic.gdx.ai.btree.Task
import com.badlogic.gdx.ai.btree.branch.DynamicGuardSelector
import com.badlogic.gdx.math.MathUtils.clamp
import org.fttbot.behavior.BBUnit
import org.fttbot.board
import org.fttbot.info.*
import org.openbw.bwapi4j.unit.Armed
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker
import java.lang.Math.pow
import kotlin.math.max
import kotlin.math.min

class UtilitySelector<E>() : DynamicGuardSelector<E>() {
    constructor(vararg tasks: UtilityTask<E>) : this() {
        tasks.forEach { addChild(it) }
    }

    override fun run() {
        val childToRun = children.maxBy { a -> (a as UtilityTask<E>).utility(board()) }
        if (runningChild != null && runningChild !== childToRun) {
            runningChild.cancel()
            runningChild = null
        }
        if (childToRun == null) {
            fail()
        } else {
            if (runningChild == null) {
                runningChild = childToRun
                runningChild.setControl(this)
                runningChild.start()
            }
            runningChild.run()
        }
    }
}

class UtilityTask<E>() : Decorator<E>() {
    lateinit var utility: (E) -> Double

    constructor(wrappedTask: Task<E>, utility: (E) -> Double) : this() {
        child = wrappedTask
        this.utility = utility
    }

    override fun copyTo(task: Task<E>?): Task<E> {
        val copy = super.copyTo(task) as UtilityTask<E>
        copy.utility = utility
        return copy
    }
}

fun expF(range: Double, x: Double, k: Double) = max(0.0, pow(range - x, k) / pow(range, k))
fun expI(range: Double, x: Double, k: Double) = min(1.0, pow(x, k) / pow(range, k))

object Utilities {
    val caution = 1.0

    fun fallback(board: BBUnit): Double {
        val unit = board.unit as MobileUnit
        // Not engaged or shooting in this very moment? Don't fall back
        if (!unit.groundWeapon.onCooldown || !unit.airWeapon.onCooldown) return 0.0
        // Melee? Don't fall back, but maybe retreat
        if (unit.groundWeapon.onCooldown && unit.groundWeapon.isMelee()) return 0.0
        return min(1.0, unit.potentialAttackers().count {
            it is Armed && it.canAttack(unit, if (it is MobileUnit) (it.topSpeed() * max(unit.groundWeapon.cooldown(), unit.airWeapon.cooldown())).toInt() else 32)
                    && it.getWeaponAgainst(unit).type().maxRange() + 32 < unit.getWeaponAgainst(it).type().maxRange()
        } / 3.0)
    }

    fun attack(board: BBUnit): Double {
        val unit = board.unit as Armed
        val result = max(with(unit.groundWeapon) { (type().damageCooldown() - cooldown()) / cooldown().toDouble() },
                with(unit.airWeapon) { (type().damageCooldown() - cooldown()) / cooldown().toDouble() })
        board.utility.attack = result
        return result
    }

    fun runaway(board: BBUnit) : Double {
        val unit = board.unit
        return min(1.0, danger(board) * (if (unit is Worker<*>) caution * 2 else 1.0))
    }

    fun danger(board: BBUnit) : Double = clamp(2 * caution * threat(board) / (0.1 + force(board)), 0.0, 1.0)

    fun defend(board: BBUnit): Double {
        val result = min(1.0, 3 * danger(board) * value(board))
        board.utility.defend = result
        return result
    }

    fun force(board: BBUnit): Double {
        val unit = board.unit
        val result = min(1.0, expI(10.0, UnitQuery.unitsInRadius(unit.position, 300)
                .count { it is Armed && it is PlayerUnit && it.isCompleted && it.isMyUnit && (it !is Worker<*> || it.board.attacking != null) }.toDouble() -
                (if (unit.board.attacking != null) 1 else 0), 3.0))
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
        val result = if (board.construction == null) 0.0 else
            1 - threat(board)
        board.utility.construct = result
        return result
    }

    fun scout(board: BBUnit): Double = if (board.scouting == null) 0.0 else 1.0

    fun gather(board: BBUnit): Double {
        val result = if (board.unit !is Worker<*>) 0.0 else
            (1 - threat(board)) * 0.3
        board.utility.gather = result
        return result
    }
}