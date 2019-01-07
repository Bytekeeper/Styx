package org.styx.tasks

import bwapi.Color
import bwapi.TilePosition
import bwapi.UnitType
import org.styx.*
import org.styx.Context.find
import org.styx.Context.game
import org.styx.Context.reserve
import org.styx.Context.visualization
import org.styx.actions.Build

class BuildTask(override val priority: Int, private val type: UnitType, private val at: TilePosition? = null) : Task() {
    private val builderLock = UnitLock { it.type.isWorker }
    private val buildPositionLock = TilePositionLock { game.canBuildHere(it, type, builderLock.current) }
    private var started = false

    override fun reset() {
        super.reset()
        started = false
        builderLock.reset()
        buildPositionLock.reset()
    }

    override fun performExecute(): FlowStatus {
        if (buildPositionLock.current != null) {
            val position = buildPositionLock.current!!.toPosition().translated(type.width() / 2, type.height() / 2)
            val buildingCandidate = find.myBuildings.closestTo(position.x, position.y).orElse(null)
            if (buildingCandidate?.type == type && buildingCandidate.tilePosition == buildPositionLock.current) {
                if (buildingCandidate.isCompleted)
                    return FlowStatus.DONE
                return FlowStatus.RUNNING
            }

        }
        val buildTilePosition = buildPositionLock.lockOr {
            at ?: ConstructionPosition.findPositionFor(type, builderLock.current)
        } ?: return FlowStatus.RUNNING

        if (visualization.drawBuildPlan) {
            game.drawBoxMap(buildTilePosition.toPosition(), buildTilePosition.toPosition().add(type.tileSize().toPosition()), Color.White)
            val center = buildTilePosition.toPosition() + type.tileSize().toPosition().divide(2)
            game.drawBoxMap(center.translated(-type.dimensionLeft(), -type.dimensionUp()), center.translated(type.dimensionRight(), type.dimensionDown()), Color.Yellow)
            game.drawTextMap(buildTilePosition.toPosition().translated(0, -10), "$type")
        }
        val buildPosition = buildTilePosition.toPosition().translated(type.width() / 2, type.height() / 2)

        val builder = builderLock.lockOr {
            val workers = reserve.units.filter { it.type.isWorker }
            workers.filter { !it.isCarryingGas && !it.isCarryingMinerals }.minBy { it.getDistance(buildPosition) }
                    ?: workers.minBy { it.getDistance(buildPosition) }
        }
                ?: return FlowStatus.RUNNING
        val framesToConstructionSite = builder.framesTo(buildPosition)
        val futureFrames = framesToConstructionSite + 2 * 12 +
                if (started) 100 else 0
        if (reserve.acquireFor(type, futureFrames)) {
            if (visualization.drawBuildPlan) {
                game.drawTextMap(builder.position, "Build $type in ${framesToConstructionSite / 24}")
            }
            started = true
            Build.build(builder, type, buildTilePosition)
        } else {
            builderLock.release()
        }
        reserve.reserve(type.mineralPrice(), type.gasPrice())
        return FlowStatus.RUNNING
    }
}
