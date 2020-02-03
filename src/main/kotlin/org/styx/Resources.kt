package org.styx

import org.bk.ass.bt.TreeNode
import org.bk.ass.manage.GMS
import org.bk.ass.query.PositionQueries

class Resources : TreeNode() {
    override fun exec() {
        success()
        ResourceReservation.gms = GMS(Styx.self.gas(), Styx.self.minerals(), Styx.self.supplyTotal() - Styx.self.supplyUsed())
        TileReservation.reset()
        UnitReservation.availableItems = PositionQueries(Styx.units.mine, positionExtractor)
    }
}
