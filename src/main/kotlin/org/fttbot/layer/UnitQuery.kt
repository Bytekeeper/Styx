package org.fttbot.layer

import org.fttbot.FTTBot
import org.fttbot.behavior.BBUnit
import org.fttbot.estimation.EnemyModel
import org.fttbot.estimation.MAX_FRAMES_TO_ATTACK
import org.fttbot.layer.UnitQuery.ownedUnits
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit
import java.util.*

const val MAX_MELEE_RANGE = 64

val MobileUnit.isWorker get() = javaClass == SCV::class.java || javaClass == Drone::class.java || javaClass == Probe::class.java
val Weapon.onCooldown get () = cooldown() > FTTBot.latency_frames
val PlayerUnit.isMyUnit get() = player == FTTBot.self
val PlayerUnit.isEnemyUnit get() = player == FTTBot.enemy
fun Armed.getWeaponAgainst(target: Unit) = if (target.isFlying) airWeapon else groundWeapon
fun Weapon.isMelee() = this != WeaponType.None && type().maxRange() <= MAX_MELEE_RANGE
fun Weapon.inRange(distance: Int, safety: Int): Boolean =
        type().minRange() <= distance && type().maxRange() >= distance - safety
val PlayerUnit.board get() = userData as BBUnit

fun Unit.canAttack(target: Unit, safety: Int = 0): Boolean {
    if (this !is Armed) return false
    val distance = getDistance(target)
    return getWeaponAgainst(target).inRange(distance, safety)
}

fun Unit.potentialAttackers(safety: Int = 16): List<Unit> =
        (UnitQuery.unitsInRadius(position, 300) + EnemyModel.seenUnits.filter { it.getDistance(this) < 300 })
                .filter { it is PlayerUnit && it.isEnemyUnit && it.canAttack(this, safety + (it.topSpeed() * MAX_FRAMES_TO_ATTACK).toInt()) }

fun Unit.canBeAttacked(safety: Int = 16) = !potentialAttackers(safety).isEmpty()
fun TrainingFacility.trains() = UnitType.values().filter { (this as Unit).isA(it.whatBuilds().first) }


object UnitQuery {
    lateinit private var allUnits: Collection<Unit>

    fun update(allUnits: Collection<Unit>) {
        this.allUnits = allUnits.filter { it.isVisible }
    }

    val minerals get() = allUnits.filter { it is MineralPatch }
    val geysers get() = allUnits.filter { it is VespeneGeyser }
    val ownedUnits get() = allUnits.filterIsInstance(PlayerUnit::class.java)
    val myUnits get() = ownedUnits.filter { it.player == FTTBot.self }
    val enemyUnits get() = ownedUnits.filter { it.player == FTTBot.enemy }
    val myBases get() = myUnits.filter { it is Hatchery || it is CommandCenter || it is Nexus }
    val myWorkers get() = myUnits.filter { it.isCompleted && it is Worker<*> }.map { it as Worker<*> }

    fun allUnits(): Collection<Unit> = allUnits
    fun unitsInRadius(position: Position, radius: Int) = allUnits.filter { it.getDistance(position) <= radius }
}