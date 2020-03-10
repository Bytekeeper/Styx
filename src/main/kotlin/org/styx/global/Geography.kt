package org.styx.global

import bwapi.UnitType
import bwapi.WalkPosition
import bwem.Area
import bwem.ChokePoint
import org.bk.ass.bt.TreeNode
import org.bk.ass.grid.BooleanGrid
import org.bk.ass.grid.Grid
import org.bk.ass.grid.Grids
import org.bk.ass.grid.RayCaster
import org.bk.ass.path.PPJps
import org.styx.Styx
import org.styx.Styx.game
import org.styx.Styx.map
import org.styx.Timed
import org.styx.toWalkPosition

class Geography : TreeNode() {
    lateinit var escapePaths: Map<Area, Map<ChokePoint, List<WalkPosition>>>
        private set
    lateinit var walkable: Grid<Boolean>
        private set
    lateinit var unitObstacles: BooleanGrid
    lateinit var walkRay: RayCaster<Boolean>
        private set
    lateinit var jps: PPJps

    override fun exec() {
        unitObstacles.updateVersion()
        Styx.units.allunits.filter {
                    !it.flying && (
                            it.unitType.isBuilding
                                    || it.unitType == UnitType.Zerg_Egg
                                    || it.unitType == UnitType.Zerg_Lurker_Egg
                                    || it.unitType == UnitType.Terran_Siege_Tank_Siege_Mode
                            )
                }
                .map {
                    it.walkRectangle.forEach { pos ->
                        if (pos.x >= 0 && pos.y >= 0 && pos.x < game.mapWidth() * 4 && pos.y < game.mapHeight()) {
                            unitObstacles.set(pos.x, pos.y, true)
                        }
                    }
                }
    }

    override fun init() {
        super.init()
        success()
        walkable = Grids.fromWalkability(game)
        unitObstacles = BooleanGrid(game.mapWidth() * 4, game.mapHeight() * 4)
        walkRay = RayCaster(Grids.oredGrid(walkable, Grids.negated(unitObstacles)))
        jps = PPJps(walkable)

        val timed = Timed()
        escapePaths = map.areas.mapNotNull { area ->
            val areaMiddle = area.walkPositionWithHighestAltitude
            val toCPPaths = area.chokePoints.mapNotNull { cp ->
                val path =
                        jps.findPath(org.bk.ass.path.Position.of(cp.center), org.bk.ass.path.Position.of(areaMiddle))
                                .path
                                .map { it.toWalkPosition() }
                if (path.isEmpty())
                    null
                else
                    cp to path
            }.toMap()
            if (toCPPaths.isEmpty())
                null
            else
                area to toCPPaths
        }.toMap()
        println("Path generation time: ${timed.ms()} ms")
    }
}