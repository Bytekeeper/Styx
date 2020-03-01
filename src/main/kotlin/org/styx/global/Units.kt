package org.styx.global

import bwapi.Unit
import bwapi.UnitType
import org.bk.ass.bt.TreeNode
import org.bk.ass.query.PositionQueries
import org.styx.PendingUnit
import org.styx.SUnit
import org.styx.Styx
import org.styx.positionExtractor

class Units : TreeNode() {
    lateinit var ownedUnits: PositionQueries<SUnit>
        private set
    lateinit var allunits: PositionQueries<SUnit>
        private set
    lateinit var mine: PositionQueries<SUnit>
        private set
    var enemy: PositionQueries<SUnit> = PositionQueries(emptyList(), positionExtractor)
        private set
    lateinit var myWorkers: PositionQueries<SUnit>
        private set
    lateinit var myResourceDepots: PositionQueries<SUnit>
        private set
    lateinit var resourceDepots: PositionQueries<SUnit>
        private set
    lateinit var minerals: PositionQueries<SUnit>
        private set
    lateinit var geysers: PositionQueries<SUnit>
        private set
    lateinit var myPending: List<PendingUnit>
        private set

    override fun exec() {
        success()
        val knownUnits = (Styx.game.allUnits.map { SUnit.forUnit(it) } + enemy).distinct()
        knownUnits.forEach {
            it.update()
            if (it.firstSeenFrame == Styx.frame)
                Styx.diag.onFirstSeen(it)
        }
        val relevantUnits = knownUnits
                .filter {
                    it.visible ||
                            !Styx.game.isVisible(it.tilePosition) && Styx.frame - 24 * 120 <= it.lastSeenFrame ||
                            Styx.frame - 24 * 4 <= it.lastSeenFrame ||
                            !it.detected ||
                            it.unitType.isBuilding
                }
        allunits = PositionQueries(relevantUnits, positionExtractor)
        ownedUnits = PositionQueries(relevantUnits.filter { it.owned }, positionExtractor)
        minerals = PositionQueries(relevantUnits.filter { it.unitType.isMineralField }, positionExtractor)
        geysers = PositionQueries(relevantUnits.filter { it.unitType == UnitType.Resource_Vespene_Geyser }, positionExtractor)

        mine = PositionQueries(relevantUnits.filter { it.myUnit }, positionExtractor)
        resourceDepots = PositionQueries(ownedUnits.filter { it.unitType.isResourceDepot }, positionExtractor)
        myResourceDepots = PositionQueries(resourceDepots.filter { it.unitType.isResourceDepot }, positionExtractor)
        myWorkers = PositionQueries(mine.filter { it.unitType.isWorker }, positionExtractor)
        enemy = PositionQueries((relevantUnits.filter { it.enemyUnit }), positionExtractor)
        myPending = mine
                .filter { !it.isCompleted }
                .map {
                    if (it.remainingBuildTime == 0 && it.unitType != UnitType.Zerg_Larva && it.unitType != UnitType.Zerg_Egg)
                        PendingUnit(it, it.position, it.unitType, 0)
                    else
                        PendingUnit(it, it.position, it.buildType, it.remainingBuildTime)
                }
//        if (game.bullets.any { it.source?.type == UnitType.Terran_Bunker || it.type == BulletType.Gauss_Rifle_Hit && it.source == null }) {
//            println("BUNKER?")
//        }
    }

    fun my(type: UnitType) = mine.filter { it.unitType == type }

    fun myCompleted(type: UnitType) = mine.filter {
        it.unitType == type && it.isCompleted ||
                type == UnitType.Zerg_Hatchery && (it.unitType == UnitType.Zerg_Lair || it.unitType == UnitType.Zerg_Hive) ||
                type == UnitType.Zerg_Lair && it.unitType == UnitType.Zerg_Hive
    }

    fun onUnitDestroy(unit: Unit) {
        val u = SUnit.forUnit(unit)
        enemy.remove(u)
        u.dispose()
    }
}