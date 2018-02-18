package org.fttbot.behavior

import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.TechType
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.type.UpgradeType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit
import java.util.*

open class BBUnit(val unit: PlayerUnit) {
    var nextOrderFrame: Int = 0
    var moveTarget: Position? = null
    var target: Unit? = null
    var status: String = "<Nothing>"
    var goal: Goal? = null

    var combatSuccessProbability: Double = 0.5
    val utility = UtilityStats()
    val importance: Double = 1.0
}

class UtilityStats(var attack: Double = 0.0,
                   var defend: Double = 0.0,
                   var force: Double = 0.0,
                   var collateralThreat: Double = 0.0,
                   var worth: Double = 0.0,
                   var construct: Double = 0.0,
                   var gather: Double = 0.0,
                   var immediateThreat: Double = 0.0,
                   var runaway: Double = 0.0)

abstract class Goal(val name: String)
abstract class GoalWithStart(name: String) : Goal(name) {
    var started = false
}
class Repairing(val target: Mechanical) : Goal("Repair")
class BeRepairedBy(val worker: SCV) : Goal("Being repaired")
class IdleAt(val position: Position) : Goal("Idling")
class Train(val type: UnitType) : Goal("Training") {
    var started = false
}
class BuildAddon(val type: UnitType) : GoalWithStart("Building Addon") {
    init {
        if (!type.isAddon) throw IllegalStateException("Not an addon: $type")
    }
}
class Research(val type: TechType) : GoalWithStart("Researching")

class Upgrade(val type: UpgradeType) : GoalWithStart("Upgrading")

class Construction(val type: UnitType, var position: TilePosition) : GoalWithStart("Construct") {
    // Worker called "build"
    var commissioned: Boolean = false
    var building: Building? = null
}

class ResumeConstruction(val building: Building) : Goal("Construct")

class Scouting(val locations: Deque<Position>) : Goal("Scout") {
    var points: List<Position>? = null
    var index: Int = 0
}

class Defending : Goal("Defending")

class Gathering(var target: Unit?) : Goal("Gathering")

class Attacking : Goal("Attack")

object ScoutingBoard {
    var lastScoutFrameCount: Int = 0
}