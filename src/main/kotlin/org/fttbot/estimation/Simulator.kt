package org.fttbot.estimation

import org.fttbot.layer.UnitLike
import java.util.*
import kotlin.math.max
import kotlin.math.min

object Simulator {
    private val prng = Random(

    )
    fun simulate(a: List<UnitLike>, b: List<UnitLike>): Double {
        var sum = 0.0
        repeat(10) {
            val sa = a.map { SimUnit.of(it) }.toMutableList()
            val sb = b.map { SimUnit.of(it) }.toMutableList()
            repeat(10) {
                if (sa.isEmpty() || sb.isEmpty()) return@repeat
                val actionsA = sa.map { Pair(it, sb[prng.nextInt(sb.size)]) }
                val actionsB = sb.map { Pair(it, sa[prng.nextInt(sa.size)]) }
                val actionTime = min(sa.map { max(it.airWeaponCooldown, it.groundWeaponCooldown) }.min() ?: 0,
                        sb.map { max(it.airWeaponCooldown, it.groundWeaponCooldown) }.min() ?: 0)
                actionsA.forEach { (u, t) ->
                    if (u.groundWeaponCooldown > 0) {
                        u.groundWeaponCooldown = max(0, u.groundWeaponCooldown - actionTime)
                        u.airWeaponCooldown = u.groundWeaponCooldown
                    } else {
                        t.hitPoints -= u.directDamage(t).toInt()
                        if (t.hitPoints <= 0) sb.remove(t)
                        val cd = u.determineWeaponAgainst(t).damageCooldown
                        u.groundWeaponCooldown = cd
                        u.airWeaponCooldown = cd
                    }
                }
                actionsB.forEach { (u, t) ->
                    if (u.groundWeaponCooldown > 0) {
                        u.groundWeaponCooldown = max(0, u.groundWeaponCooldown - actionTime)
                        u.airWeaponCooldown = u.groundWeaponCooldown
                    } else {
                        t.hitPoints -= u.directDamage(t).toInt()
                        if (t.hitPoints <= 0) sa.remove(t)
                        val cd = u.determineWeaponAgainst(t).damageCooldown
                        u.groundWeaponCooldown = cd
                        u.airWeaponCooldown = cd
                    }
                }
            }
            sum += CombatEval.probabilityToWin(sa, sb)
        }
        return sum / 10.0
    }
}
