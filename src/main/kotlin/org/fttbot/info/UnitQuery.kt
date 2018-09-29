package org.fttbot.info

import org.fttbot.*
import org.fttbot.estimation.MAX_FRAMES_TO_ATTACK
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.WeaponType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val MAX_MELEE_RANGE = 64

fun UnitType.isMorphedFromOtherBuilding() = whatBuilds().unitType.isBuilding

val Weapon.onCooldown get () = cooldown() >= FTTBot.latency_frames
val PlayerUnit.isMyUnit get() = player == FTTBot.self
val PlayerUnit.isEnemyUnit get() = player in FTTBot.enemies
fun PlayerUnit.getWeaponAgainst(target: Unit) =
        if (target.isFlying && this is AirAttacker) airWeapon
        else if (!target.isFlying && this is GroundAttacker) groundWeapon
        else if (this is Bunker) Weapon(UnitType.Terran_Marine.groundWeapon(), 0)
        else Weapon(WeaponType.None, -1)

fun PlayerUnit.maxRangeVs(target: Unit) =
        player.unitStatCalculator.weaponMaxRange(getWeaponAgainst(target).type()) +
                (if (this is Bunker) 64 else 0)

fun PlayerUnit.maxRange() =
        (if (this is GroundAttacker) player.unitStatCalculator.weaponMaxRange(groundWeapon.type())
        else player.unitStatCalculator.weaponMaxRange((this as AirAttacker).airWeapon.type())) +
                (if (this is Bunker) 64 else 0)

fun Weapon.isMelee() = this != WeaponType.None && type().maxRange() <= MAX_MELEE_RANGE

fun <T : Unit> Collection<T>.closestTo(unit: Unit) = minBy { it.getDistance(unit) }


fun PlayerUnit.isOccupied() = !ResourcesBoard.units.contains(this)
val PlayerUnit.isSuicideUnit
    get() = when (this) {
        is Scourge, is SpiderMine, is Scarab -> true
        else -> false
    }

fun PlayerUnit.isFasterThan(other: PlayerUnit) =
        (this is MobileUnit && other !is MobileUnit) ||
                (this is MobileUnit && other is MobileUnit && this.topSpeed > other.topSpeed)


// From https://docs.google.com/spreadsheets/d/1bsvPvFil-kpvEUfSG74U3E5PLSTC02JxSkiR8QdLMuw/edit#gid=0 resp. PurpleWave
val Attacker.stopFrames
    get() = when (this) {
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

fun findWorker(forPosition: Position? = null, maxRange: Double = 800.0, candidates: List<Worker> = ResourcesBoard.completedUnits.filterIsInstance<Worker>()): Worker? {
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
fun Weapon.attackIsComplete(unit: Attacker) = type().damageCooldown() == 0 || type().damageCooldown() - cooldown() >= unit.stopFrames

val Attacker.canMoveWithoutBreakingAttack
    get() = (this as? GroundAttacker)?.groundWeapon?.attackIsComplete(this) != false &&
            (this as? AirAttacker)?.airWeapon?.attackIsComplete(this) != false

fun Attacker.hasWeaponAgainst(target: Unit) =
        WeaponType.None != getWeaponAgainst(target).type()

fun PlayerUnit.canAttack(target: Unit, safety: Int = 0): Boolean {
    if (!isCompleted) return false
    val weaponType = getWeaponAgainst(target).type()
    if (weaponType == WeaponType.None) return false
    val distance = getDistance(target)
    val maxWeaponRange = player.unitStatCalculator.weaponMaxRange(weaponType)
    return distance <= maxWeaponRange + safety && distance > weaponType.minRange() && target.isVisible && (target !is Cloakable && target !is Burrowable || target is PlayerUnit && target.isDetected)
}

fun Unit.potentialAttackers(safety: Int = 16): List<PlayerUnit> =
        (UnitQuery.allUnits.inRadius(position, 400).filterIsInstance<PlayerUnit>() + EnemyInfo.seenUnits.filter { it.getDistance(this) < 400 })
                .filter {
                    it.isEnemyUnit && it.canAttack(this, safety + (((it as? MobileUnit)?.topSpeed
                            ?: 0.0) * MAX_FRAMES_TO_ATTACK).toInt())
                }

fun PlayerUnit.canBeAttacked(safety: Int = 16) = !potentialAttackers(safety).isEmpty()
fun TrainingFacility.trains() = UnitType.values().filter { type == it.whatBuilds().unitType }
fun UnitType.allRequiredUnits() = allRequiredUnits(HashSet())
private fun UnitType.allRequiredUnits(set: HashSet<UnitType>): HashSet<UnitType> {
    if (set.contains(this)) return set
    if (this.gasPrice() > 0) set.add(race.refinery)
    set.add(this)
    requiredUnits().keys.forEach { it.allRequiredUnits(set) }
    whatBuilds().unitType.allRequiredUnits(set)
    return set
}

fun UnitType.whatNeedsToBeBuild(): List<UnitType> {
    val result = ArrayList<UnitType>()
    var current = this;
    while (!current.isWorker) {
        result.add(current)
        current = current.whatBuilds().unitType
    }
    return result
}

class RadiusCache<T : Unit>(val units: Collection<T>) : Collection<T> by units {
    private val map = TreeMap<PositionAndId, T>()

    init {
        units.forEach {
            map[PositionAndId(it.position.x, it.position.y, it.id)] = it
        }
    }

    fun within(a: Position, b: Position): Collection<T> =
            map.subMap(PositionAndId(a.x, a.y, -1), true,
                    PositionAndId(b.x, b.y, -1), true).values

    fun inRadius(unit: Unit, radius: Int): Collection<T> =
            within(Position(unit.x - radius - 150, unit.y - radius - 150),
                    Position(unit.x + radius + 150, unit.y + radius + 150))
                    .filter { it.getDistance(unit) <= radius }

    fun inRadius(position: Position, radius: Int): Collection<T> =
            within(Position(position.x - radius - 150, position.y - radius - 150),
                    Position(position.x + radius + 150, position.y + radius + 150))
                    .filter { it.getDistance(position) <= radius }

    fun closestTo(unit: Unit): T? {
        val query = PositionAndId(unit.x, unit.y, -1)
        val lower = map.lowerEntry(query)
        val higher = map.higherEntry(query)
        val lbox = if (lower != null) max(abs(lower.value.x - unit.x), abs(lower.value.y - unit.y)) else 30000
        val hbox = if (higher != null) max(abs(higher.value.x - unit.x), abs(higher.value.y - unit.y)) else 30000
        val boxSize = min(lbox, hbox)
        val queryBoxSize = Position(boxSize, boxSize)
        return within(unit.position - queryBoxSize, unit.position + queryBoxSize)
                .minBy { it.getDistance(unit) }
    }

    private class PositionAndId(val x: Int, val y: Int, val id: Int) : Comparable<PositionAndId> {
        override fun compareTo(other: PositionAndId): Int =
                if (x < other.x) -1 else if (x > other.x) 1 else
                    if (y < other.y) -1 else if (y > other.y) 1 else
                        Integer.compare(id, other.id)
    }
}

object UnitQuery {
    lateinit var allUnits: RadiusCache<Unit> private set
    lateinit var myUnits: RadiusCache<PlayerUnit> private set
    lateinit var ownedUnits: RadiusCache<PlayerUnit> private set
    lateinit var enemyUnits: RadiusCache<PlayerUnit> private set
    lateinit var myWorkers: RadiusCache<Worker> private set
    lateinit var minerals: RadiusCache<MineralPatch> private set
    lateinit var geysers: RadiusCache<VespeneGeyser> private set
    lateinit var myBuildings: RadiusCache<Building> private set
    lateinit var myEggs: List<Egg> private set
    lateinit var myCompletedUnits: RadiusCache<PlayerUnit>
    private val myUnitByType = mutableMapOf<Any, LazyOnFrame<RadiusCache<PlayerUnit>>>()

    fun reset() {
        andStayDown.clear()
        update(emptyList())
    }


    fun update(allUnits: Collection<Unit>) {
        this.allUnits = RadiusCache(allUnits.filter { it.isVisible && !andStayDown.contains(Pair(it.id, it.type)) })
        minerals = RadiusCache(allUnits.filterIsInstance<MineralPatch>())
        geysers = RadiusCache(allUnits.filterIsInstance<VespeneGeyser>())
        ownedUnits = RadiusCache(this.allUnits.filterIsInstance<PlayerUnit>())
        myUnits = RadiusCache(ownedUnits.filter { it.player == FTTBot.self && it.exists() })
        myCompletedUnits = RadiusCache(myUnits.filter { it.isCompleted })
        enemyUnits = RadiusCache(ownedUnits.filter { it.player in FTTBot.enemies })
        myWorkers = RadiusCache(myCompletedUnits.filterIsInstance<Worker>())
        myBuildings = RadiusCache(myCompletedUnits.filterIsInstance<Building>())
        myEggs = myUnits.filterIsInstance(Egg::class.java)
    }

    // BWAPI sometimes "provides" units that aren't there - but those are also reported "hidden", so just ignore them
    val andStayDown = mutableSetOf<Pair<Int, UnitType>>()

    fun inRadius(position: Position, radius: Int) = allUnits.inRadius(position, radius)

    inline fun <reified T : PlayerUnit> my() = my(T::class.java)

    fun <T : PlayerUnit> my(type: Class<T>) = myUnitByType.computeIfAbsent(type) {
        LazyOnFrame { RadiusCache(myUnits.filter { u -> type.isInstance(u) }) }
    }.value as RadiusCache<T>
}