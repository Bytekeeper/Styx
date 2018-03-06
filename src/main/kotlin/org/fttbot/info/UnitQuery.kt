package org.fttbot.info

import org.fttbot.FTTBot
import org.fttbot.estimation.MAX_FRAMES_TO_ATTACK
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit
import sun.management.snmp.jvminstr.JvmThreadInstanceEntryImpl.ThreadStateMap.Byte1.other

const val MAX_MELEE_RANGE = 64

val Weapon.onCooldown get () = cooldown() >= FTTBot.latency_frames
val PlayerUnit.isMyUnit get() = player == FTTBot.self
val PlayerUnit.isEnemyUnit get() = player == FTTBot.enemy
fun Armed.getWeaponAgainst(target: Unit) = if (target.isFlying) airWeapon else groundWeapon
fun Weapon.isMelee() = this != WeaponType.None && type().maxRange() <= MAX_MELEE_RANGE
fun WeaponType.inRange(distance: Int, safety: Int): Boolean =
        minRange() <= distance && maxRange() >= distance - safety

fun <T : Unit> List<T>.inRadius(other: Unit, maxRadius: Int) = filter { other.getDistance(it) < maxRadius }
fun <T : Unit> List<T>.inRadius(position: Position, maxRadius: Int) = filter { it.getDistance(position) < maxRadius }

// From https://docs.google.com/spreadsheets/d/1bsvPvFil-kpvEUfSG74U3E5PLSTC02JxSkiR8QdLMuw/edit#gid=0 resp. PurpleWave
val Armed.stopFrames get() = when (this) {
    is Goliath, is SiegeTank, is Reaver -> 1
    is Worker, is Vulture, is Wraith, is BattleCruiser, is Scout, is Lurker -> 2
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

fun findWorker(forPosition: Position? = null, maxRange: Double = 800.0, candidates: List<Worker> = UnitQuery.myWorkers): Worker? {
    val selection =
            if (forPosition != null) {
                candidates.filter { it.getDistance(forPosition) <= maxRange }
            } else candidates
    return selection.minWith(Comparator { a, b ->
        if (a.isIdle != b.isIdle) {
            if (a.isIdle) -1 else 1
        } else if (a.isGatheringMinerals != b.isGatheringMinerals) {
            if (a.isGatheringMinerals) -1 else 1
        } else if ((a.isGatheringMinerals && !a.isCarryingMinerals) != (b.isGatheringMinerals && !b.isCarryingMinerals)) {
            if (a.isGatheringMinerals && !a.isCarryingMinerals) -1 else 1
        } else if (a.isGatheringGas != b.isGatheringGas) {
            if (a.isGatheringGas) -1 else 1
        } else if ((a.isGatheringGas && !a.isCarryingGas) != (b.isGatheringGas && !b.isCarryingGas)) {
            if (a.isGatheringGas && !a.isCarryingGas) -1 else 1
        } else if (a.isConstructing != b.isConstructing) {
            if (a.isConstructing) -1 else 1
        } else if (forPosition != null) {
            a.position.getDistance(forPosition).compareTo(b.position.getDistance(forPosition))
        } else 0
    })
}


//  + FTTBot.remaining_latency_frames
fun Weapon.attackIsComplete(unit: Armed) = type().damageCooldown() == 0 || type().damageCooldown() - cooldown() >= unit.stopFrames

val Armed.canMoveWithoutBreakingAttack get() = groundWeapon.attackIsComplete(this) && airWeapon.attackIsComplete(this)

fun PlayerUnit.canAttack(target: Unit, safety: Int = 0): Boolean {
    if (!isCompleted) return false
    val weaponType = if (this is Bunker) UnitType.Terran_Marine.groundWeapon() else if (this is Armed) getWeaponAgainst(target).type() else return false
    val distance = getDistance(target)
    return weaponType.inRange(distance, safety) && target.isVisible && (target !is Cloakable && target !is Burrowable || target is PlayerUnit && target.isDetected)
}

fun Unit.potentialAttackers(safety: Int = 16): List<PlayerUnit> =
        (UnitQuery.allUnits().inRadius(position, 300).filterIsInstance(PlayerUnit::class.java) + EnemyState.seenUnits.filter { it.getDistance(this) < 300 })
                .filter { it.isEnemyUnit && it.canAttack(this, safety + (it.topSpeed() * MAX_FRAMES_TO_ATTACK).toInt()) }

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
    lateinit var allUnits: List<Unit> private set
    lateinit var myUnits: List<PlayerUnit> private set


    fun update(allUnits: Collection<Unit>) {
        this.allUnits = allUnits.filter { it.isVisible }
        myUnits = ownedUnits.filter { it.player == FTTBot.self }
    }

    val minerals get() = allUnits.filterIsInstance(MineralPatch::class.java)
    val geysers get() = allUnits.filter { it is VespeneGeyser }
    val ownedUnits get() = allUnits.filterIsInstance(PlayerUnit::class.java)
    val enemyUnits get() = ownedUnits.filter { it.player == FTTBot.enemy }
    val myBases get() = myUnits.filter { it is Base }
    val myWorkers get() = myUnits.filterIsInstance(Worker::class.java).filter { it.isCompleted }

    fun allUnits(): List<Unit> = allUnits
    fun inRadius(position: Position, radius: Int) = allUnits.inRadius(position, radius)
}