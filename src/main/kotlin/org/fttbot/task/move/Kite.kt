package org.fttbot.task.move

import org.fttbot.*
import org.fttbot.info.*
import org.fttbot.task.Action
import org.fttbot.task.CombatData
import org.fttbot.task.TaskStatus
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit

class Kite(private val attacker: Attacker, private val combatData: CombatData) : Action() {
    override fun processInternal(): TaskStatus {
        val position = attacker.position
        val targetUnit = attacker.targetUnit
        attacker as MobileUnit
        if (attacker.maxRange() > 64 && attacker.potentialAttackers(32).isEmpty()) {
            val enemies = combatData.enemies
            enemies.asSequence()
                    .filter {
                        attacker.hasWeaponAgainst(it) && it.isDetected &&
                                (it !is Attacker || it.maxRangeVs(attacker) < attacker.maxRangeVs(it)) &&
                                (it !is MobileUnit || it.topSpeed < attacker.topSpeed)
                    }.filter { target ->
                        val pos = position.minus(target.position).toVector().setLength(attacker.maxRangeVs(target).toFloat()).toPosition().plus(target.position)
                        enemies.none { e ->
                            val wpn = e.getWeaponAgainst(attacker)
                            wpn.type().damageAmount() > 0 && wpn.maxRange() >= e.getDistance(pos) && (target != e || e.hitPoints > 10)
                        }
                    }.minBy { it.getDistance(attacker) }
                    ?.let {
                        MyInfo.unitStatus[attacker] = "Kiting"
                        if (targetUnit != it && it.isVisible) {
                            Commands.attack(attacker, it)
                        } else if (attacker.targetPosition.getDistance(it.position) > 64) {
                            Commands.move(attacker, it.position)
                        }
                        return TaskStatus.RUNNING
                    }
        }
        return TaskStatus.DONE
    }

}