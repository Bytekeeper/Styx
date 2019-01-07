package org.styx

import bwapi.*
import bwapi.Unit
import org.styx.Context.frameCount
import org.styx.Context.turnSize
import kotlin.math.max

class Command {
    private val nextCommandFrame = mutableMapOf<Unit, Int>()

    fun build(unit: Unit, buildPosition: TilePosition, type: UnitType) {
        require(unit.type.isWorker)
        if (unit.isNotReady || unit.order == Order.IncompleteBuilding) return
        unit.build(type, buildPosition)
        sleepBuild(unit)
    }

    fun attack(unit: Unit, target: Unit) {
        if (unit.isNotReady) return
        unit.attack(target)
        sleepAttack(unit)
    }

    fun attack(unit: Unit, target: Position) {
        if (unit.isNotReady) return
        unit.attack(target)
        sleepAttack(unit)
    }

    fun gather(unit: Unit, target: Unit) {
        require(unit.type.isWorker)
        require(target.type.isMineralField || target.type.isRefinery)
        if (unit.isNotReady) return
        unit.gather(target)
        sleep(unit)
    }

    fun train(trainer: Unit, type: UnitType) {
        require(type.whatBuilds().first == trainer.type)
        if (trainer.isNotReady) return
        trainer.train(type)
        sleep(trainer)
    }

    fun move(unit: Unit, to: Position) {
        if (unit.isNotReady) return
        unit.move(to)
        sleep(unit)
    }

    private fun sleepAttack(unit: Unit) {
        sleep(unit, max(turnSize, unit.stopFrames))
    }

    private fun sleepBuild(unit: Unit) {
        // Stolen from PurpleWave
        sleep(unit, 7)
    }

    private val Unit.isNotReady get() = nextCommandFrame[this] ?: 0 > frameCount

    private fun sleep(unit: Unit, frames: Int = 0) {
        nextCommandFrame[unit] = max(
                nextCommandFrame[unit] ?: 0,
                frameCount +
                        arrayOf(frames,
                                turnSize
                        ).max()!!
        )
    }

}