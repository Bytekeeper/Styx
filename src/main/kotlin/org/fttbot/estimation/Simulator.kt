package org.fttbot.estimation

import bwapi.Position
import com.badlogic.gdx.math.MathUtils.clamp
import org.fttbot.*
import org.fttbot.layer.UnitLike
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object Simulator {
    private val prng = Random(

    )

    fun simulate(a: List<UnitLike>, b: List<UnitLike>, times: Int = 30, depth : Int = 10): Double {
        var sum = 0.0
        repeat(times) {
            val sa = a.map { SimUnit.of(it) }.toMutableList()
            val sb = b.map { SimUnit.of(it) }.toMutableList()
            val ctx = SimContext(sa, sb)
            repeat(depth) { ctx.step() }
            sum += CombatEval.probabilityToWin(sa, sb)
        }
        return sum / times
    }

    class SimContext(val unitsA: MutableList<SimUnit>, val unitsB: MutableList<SimUnit>) {
        fun step() {
            if (unitsA.isEmpty() || unitsB.isEmpty()) return

            val actionsA = unitsA.map { selectAction(it, unitsB) }
            val actionsB = unitsB.map { selectAction(it, unitsA) }

            val cooldown = min(actionsA.map { it.cooldown }.min()!!, actionsB.map { it.cooldown }.min()!!)
            unitsA.forEach {
                it.groundWeaponCooldown = max(0, it.groundWeaponCooldown - cooldown)
                it.airWeaponCooldown = max(0, it.airWeaponCooldown - cooldown)
                it.framesPaused = max(0, it.framesPaused - cooldown)
            }
            unitsB.forEach {
                it.groundWeaponCooldown = max(0, it.groundWeaponCooldown - cooldown)
                it.airWeaponCooldown = max(0, it.airWeaponCooldown - cooldown)
                it.framesPaused = max(0, it.framesPaused - cooldown)
            }
            actionsA.forEach { it.apply(cooldown) }
            actionsB.forEach { it.apply(cooldown) }

            unitsA.removeIf { it.hitPoints == 0 }
            unitsB.removeIf { it.hitPoints == 0 }
        }

        private fun selectAction(unit: SimUnit, enemies: MutableList<SimUnit>): Action {
            if (unit.framesPaused == 0) {
                val x = prng.nextFloat()
                // Best Attack-Value
                if (x < 0.17) {
                    val bestAttack = enemies.map { Attack(unit, it) }.filter { it.possible }.maxBy { unit.damagePerFrameTo(it.target) / it.target.hitPoints }
                    if (bestAttack != null) return bestAttack
                }
                // Closest Enemy
                if (x < 0.34) {
                    val bestAttack = enemies.map { Attack(unit, it) }.filter { it.possible }.minBy { unit.distanceTo(it.target) }
                    if (bestAttack != null) return bestAttack
                }
                // Weakest Enemy
                if (x < 0.5) {
                    val bestAttack = enemies.map { Attack(unit, it) }.filter { it.possible && unit.directDamage(it.target) > 0 }.minBy { it.target.hitPoints }
                    if (bestAttack != null) return bestAttack
                }
                if (unit.canMove) {
                    val unitPos = unit.position!!
                    // Kite Closest
                    if (x < 0.7) {
                        val bestAttack = enemies.map { Attack(unit, it) }.minBy { unit.distanceTo(it.target) }
                        if (bestAttack != null) {
                            val enemyPos = bestAttack.target.position!!
                            val targetPosition = unitPos.toVector().sub(enemyPos.toVector())
                                    .setLength(unit.determineWeaponAgainst(bestAttack.target).maxRange.toFloat())
                                    .add(enemyPos.toVector())
                            return Move(unit, targetPosition.toPosition())
                        }
                    }
                    // Kite Best Attack-Value
                    if (x < 0.90) {
                        val bestAttack = enemies.map { Attack(unit, it) }.maxBy { unit.damagePerFrameTo(it.target) / it.target.hitPoints }
                        if (bestAttack != null) {
                            val enemyPos = bestAttack.target.position!!
                            val targetPosition = unitPos.toVector().sub(enemyPos.toVector())
                                    .setLength(unit.determineWeaponAgainst(bestAttack.target).maxRange.toFloat())
                                    .add(enemyPos.toVector())
                            return Move(unit, targetPosition.toPosition())
                        }
                    }

                    if (x < 0.99) {
                        val targetPosition = Position(unitPos.x + prng.nextInt(200) - 100, unitPos.y + prng.nextInt(200) - 100)
                        return Move(unit, targetPosition)
                    }
                }
            }
            return Idle(max(unit.framesPaused, 1))
        }
    }


    interface Action {
        val possible: Boolean
        val cooldown: Int
        fun apply(frames: Int)

    }

    class Idle(frames: Int) : Action {
        override val possible = true
        override var cooldown = frames

        override fun apply(frames: Int) {
            cooldown = max(0, cooldown - frames)
        }
    }

    class Attack(val attacker: SimUnit, val target: SimUnit) : Action {
        override val possible = attacker.determineWeaponAgainst(target).maxRange >= target.distanceTo(attacker) && attacker.weaponCoolDownVs(target) == 0
        override val cooldown = max(1, attacker.weaponCoolDownVs(target))

        override fun apply(frames: Int) {
            if (attacker.weaponCoolDownVs(target) > 0) throw IllegalStateException()

            attacker.framesPaused = 5
            target.hitPoints = max(0, (target.hitPoints - attacker.directDamage(target)).toInt())
            if (target.isAir) attacker.airWeaponCooldown = attacker.type.airWeapon.damageCooldown
            else attacker.groundWeaponCooldown = attacker.type.groundWeapon.damageCooldown
        }
    }

    class Move(val mover: SimUnit, val targetPosition: Position) : Action {
        override val possible = if (mover.canMove) true else throw IllegalStateException()

        override val cooldown = clamp(ceil((targetPosition - mover.position!!).toVector().len() / mover.type.topSpeed.toFloat()).toInt(), 1, 10)

        override fun apply(frames: Int) {
            val delta = (targetPosition - mover.position!!).toVector()
            val framesNeeded = delta.len() / mover.type.topSpeed.toFloat()
            delta.scl(min(frames.toFloat(), framesNeeded) / framesNeeded)
            mover.position = mover.position!! + delta.toPosition()
        }
    }
}
