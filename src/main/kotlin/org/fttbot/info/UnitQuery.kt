package org.fttbot.info

import org.fttbot.FTTBot
import org.fttbot.behavior.BBUnit
import org.fttbot.estimation.MAX_FRAMES_TO_ATTACK
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit

const val MAX_MELEE_RANGE = 64

val MobileUnit.isWorker get() = javaClass == SCV::class.java || javaClass == Drone::class.java || javaClass == Probe::class.java
val Weapon.onCooldown get () = cooldown() >= FTTBot.latency_frames
val PlayerUnit.isMyUnit get() = player == FTTBot.self
val PlayerUnit.isEnemyUnit get() = player == FTTBot.enemy
fun Armed.getWeaponAgainst(target: Unit) = if (target.isFlying) airWeapon else groundWeapon
fun Weapon.isMelee() = this != WeaponType.None && type().maxRange() <= MAX_MELEE_RANGE
fun Weapon.inRange(distance: Int, safety: Int): Boolean =
        type().minRange() <= distance && type().maxRange() >= distance - safety

val PlayerUnit.board get() = userData as BBUnit

// From https://docs.google.com/spreadsheets/d/1bsvPvFil-kpvEUfSG74U3E5PLSTC02JxSkiR8QdLMuw/edit#gid=0 resp. PurpleWave
val Armed.stopFrames get() = when (this) {
    is Goliath, is SiegeTank, is Reaver -> 1
    is Worker<*>, is Vulture, is Wraith, is BattleCruiser, is Scout, is Lurker -> 2
    is Ghost, is Hydralisk -> 3
    is Arbiter, is Zergling -> 4
    is Zealot, is Dragoon -> 7
    is Marine, is Firebat, is Corsair -> 8
    is DarkTemplar, is Devourer -> 9
    is Ultralisk -> 14
    is Archon -> 15
    is Valkyrie -> 40
    else -> 2
}

fun Weapon.attackIsComplete(unit: Armed) = type().damageCooldown() - cooldown() + FTTBot.remaining_latency_frames >= unit.stopFrames

val Armed.canMoveWithoutBreakingAttack get() = groundWeapon.attackIsComplete(this) && airWeapon.attackIsComplete(this)

fun Unit.canAttack(target: PlayerUnit, safety: Int = 0): Boolean {
    if (this !is Armed) return false
    val distance = getDistance(target)
    return getWeaponAgainst(target).inRange(distance, safety) && target.isVisible && (target !is Cloakable && target !is Burrowable || target.isDetected)
}

fun PlayerUnit.potentialAttackers(safety: Int = 16): List<Unit> =
        (UnitQuery.unitsInRadius(position, 300) + EnemyState.seenUnits.filter { it.getDistance(this) < 300 })
                .filter { it is PlayerUnit && it.isEnemyUnit && it.canAttack(this, safety + (it.topSpeed() * MAX_FRAMES_TO_ATTACK).toInt()) }

fun PlayerUnit.canBeAttacked(safety: Int = 16) = !potentialAttackers(safety).isEmpty()
fun TrainingFacility.trains() = UnitType.values().filter { (this as Unit).isA(it.whatBuilds().first) }
fun UnitType.allRequiredUnits() = allRequiredUnits(HashSet())
private fun UnitType.allRequiredUnits(set: HashSet<UnitType>): HashSet<UnitType> {
    if (set.contains(this)) return set
    if (this.gasPrice() > 0) set.add(race.refinery)
    set.add(this)
    requiredUnits().forEach { it.allRequiredUnits(set) }
    whatBuilds().first.allRequiredUnits(set)
    return set
}

fun UnitType.whatNeedsToBeBuild(): List<UnitType> {
    val result = ArrayList<UnitType>()
    var current = this;
    while (!current.isWorker) {
        result.add(current)
        current = current.whatBuilds().first
    }
    return result
}


object UnitQuery {
    lateinit private var allUnits: Collection<Unit>

    fun update(allUnits: Collection<Unit>) {
        UnitQuery.allUnits = allUnits.filter { it.isVisible }
    }

    val minerals get() = allUnits.filterIsInstance(MineralPatch::class.java)
    val geysers get() = allUnits.filter { it is VespeneGeyser }
    val ownedUnits get() = allUnits.filterIsInstance(PlayerUnit::class.java)
    val myUnits get() = ownedUnits.filter { it.player == FTTBot.self }
    val enemyUnits get() = ownedUnits.filter { it.player == FTTBot.enemy }
    val myBases get() = myUnits.filter { it is Hatchery || it is CommandCenter || it is Nexus }
    val myWorkers get() = myUnits.filter { it.isCompleted && it is Worker<*> }.map { it as Worker<*> }

    fun allUnits(): Collection<Unit> = allUnits
    fun unitsInRadius(position: Position, radius: Int) = allUnits.filter { it.getDistance(position) <= radius }
}