package org.fttbot.strategies

import com.badlogic.gdx.math.MathUtils
import org.fttbot.Board
import org.fttbot.FTTBot
import org.fttbot.LazyOnFrame
import org.fttbot.fastsig
import org.fttbot.info.MyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.info.inRadius
import org.openbw.bwapi4j.unit.*
import kotlin.math.max
import kotlin.math.min

object Utilities {
    val expansionUtility by LazyOnFrame {
        min(1.0, workerUtilization / (fastsig(UnitQuery.myBases.size * 2.0) + 0.1))
    }

    private val workerUtilization by LazyOnFrame {
        min(1.0, (UnitQuery.myWorkers.size.toDouble() + UnitQuery.myEggs.count { it.buildType.isWorker }) / 1.6 / (0.1 + MyInfo.myBases.sumBy {
            it as PlayerUnit; UnitQuery.minerals.inRadius(it, 300).count() + UnitQuery.myBuildings.count { it is GasMiningFacility } * 3
        }))
    }

    val moreTrainersUtility by LazyOnFrame {
        MathUtils.clamp(Board.resources.minerals / (UnitQuery.myBases.size * 500.0 + 0.1), 0.0, 1.0)
    }

    val moreWorkersUtility by LazyOnFrame {
        min(1.0 - workerUtilization * 0.8, max(0.0, max(1.0 - FTTBot.self.minerals() / 6000.0, 1.0 - FTTBot.self.gas() / 3000.0)))
    }

    val moreLurkersUtility by LazyOnFrame {
        min(1.0, UnitQuery.myWorkers.size / (UnitQuery.myUnits.count { it is Lurker || it is LurkerEgg } * 3.0 + 8.0))
    }

    val moreMutasUtility by LazyOnFrame {
        min(1.0, UnitQuery.myWorkers.size / (UnitQuery.myUnits.count { it is Mutalisk } * 3.0 + 8.0))
    }

    val moreGasUtility by LazyOnFrame {
        if (MyInfo.myBases.none { base -> base as PlayerUnit; UnitQuery.geysers.any { it.getDistance(base) < 300 } })
            0.0
        else
            min(1.0, FTTBot.self.minerals() / (UnitQuery.myUnits.count { it is GasMiningFacility } * 30.0 + FTTBot.self.gas() + 200.0))
    }

    val moreSupplyUtility by LazyOnFrame {
        val freeSupply = FTTBot.self.supplyTotal() - FTTBot.self.supplyUsed()
        if (MyInfo.pendingSupply() + FTTBot.self.supplyTotal() >= 400)
            0.0
        else
            min(1.0, min(UnitQuery.myBases.size * 8.0, FTTBot.self.minerals() / 13.0) / max(1, MyInfo.pendingSupply() + freeSupply))
    }
}