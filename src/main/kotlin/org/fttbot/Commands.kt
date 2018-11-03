package org.fttbot

import org.fttbot.info.stopFrames
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.type.Order
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*
import org.openbw.bwapi4j.unit.Unit
import kotlin.math.max

object Commands {
    private val nextCommandFrame = mutableMapOf<PlayerUnit, Int>()

    fun build(worker: Worker, buildPosition: TilePosition, type: UnitType) {
        if (worker.isNotReady || worker.order == Order.IncompleteBuilding) return
        worker.build(buildPosition, type)
        sleepBuild(worker)
    }

    fun attack(attacker: Attacker, target: Unit) {
        if (attacker.isNotReady) return
        attacker.attack(target)
        sleepAttack(attacker)
    }

    fun move(unit: MobileUnit, to: Position) {
        if (unit.isNotReady) return
        unit.move(to)
        sleep(unit)
    }

    private fun sleepAttack(unit: Attacker) {
        sleep(unit, max(FTTBot.turnSize, unit.stopFrames))
    }

    private fun sleepBuild(unit: PlayerUnit) {
        // Stolen from PurpleWave
        sleep(unit, 7)
    }

    private val PlayerUnit.isNotReady get() = nextCommandFrame[this] ?: 0 > FTTBot.frameCount

    private fun sleep(unit: PlayerUnit, frames: Int = 0) {
        nextCommandFrame[unit] = max(
                nextCommandFrame[unit] ?: 0,
                FTTBot.frameCount +
                        arrayOf(frames,
                                FTTBot.turnSize
                        ).max()!!
        )
    }

}