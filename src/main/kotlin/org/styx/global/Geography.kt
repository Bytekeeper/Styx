package org.styx.global

import org.bk.ass.bt.TreeNode
import org.bk.ass.grid.BooleanGrid
import org.bk.ass.grid.Grids
import org.bk.ass.grid.RayCaster
import org.bk.ass.path.Jps
import org.styx.Styx
import org.styx.Styx.game
import org.styx.Timed

class Geography : TreeNode() {
    private lateinit var unitObstacles: BooleanGrid
    lateinit var walkRay: RayCaster<Boolean>
        private set
    lateinit var jps: Jps

    override fun exec() {
        unitObstacles.updateVersion()
        Styx.units.allunits.filter {
            !it.flying && (
                    !it.unitType.canMove()
                            || !it.moving && it.enemyUnit
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
        val walkable = Grids.fromWalkability(game)
        unitObstacles = BooleanGrid(game.mapWidth() * 4, game.mapHeight() * 4)
        val realGrid = Grids.andedGrid(walkable, Grids.negated(unitObstacles))
        walkRay = RayCaster(realGrid)
        jps = Jps(realGrid)

        val timed = Timed()
        println("Path generation time: ${timed.ms()} ms")
    }
}