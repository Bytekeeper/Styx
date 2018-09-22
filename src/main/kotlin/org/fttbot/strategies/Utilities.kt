package org.fttbot.strategies

import com.badlogic.gdx.math.MathUtils
import org.fttbot.FTTBot
import org.fttbot.LazyOnFrame
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.estimation.SimUnit.Companion.of
import org.fttbot.fastsig
import org.fttbot.info.EnemyInfo
import org.fttbot.info.MyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.info.allRequiredUnits
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.*
import kotlin.math.max
import kotlin.math.min

object Utilities {
    val expansionUtility by LazyOnFrame {
        min(1.0, workerUtilization * (1.15 - fastsig(MyInfo.myBases.count() * 0.05)))
    }

    val workerUtilization by LazyOnFrame {
        min(1.0, (UnitQuery.myWorkers.size.toDouble() + UnitQuery.myEggs.count { it.buildType.isWorker }) / 1.6 / (0.1 + MyInfo.myBases.sumBy {
            UnitQuery.minerals.inRadius(it, 300).count() + UnitQuery.myBuildings.count { it is GasMiningFacility } * 3
        }))
    }

    val mineralsUtilization by LazyOnFrame {
        min(1.0, FTTBot.self.minerals() / 2000.0)
    }

    val gasUtilization by LazyOnFrame {
        min(1.0, FTTBot.self.gas() / 1000.0)
    }

    val moreTrainersUtility by LazyOnFrame {
        MathUtils.clamp((MyInfo.myBases.size * workerUtilization + 0.7) / (UnitQuery.my<ResourceDepot>().size + 1.0), 0.0, 1.0)
    }

    val moreWorkersUtility by LazyOnFrame {
        (1.0 - fastsig(workerUtilization * 0.8)) * max(1.0 - mineralsUtilization * 0.6, 1.0 - gasUtilization * 0.6)
    }

    fun needed(type: UnitType) = CombatEval.minAmountOfAdditionalsForProbability(UnitQuery.myUnits.filter { it is MobileUnit && it !is Worker }.map(SimUnit.Companion::of)
            .map { it.position = null; it }, of(type),
            enemySims, 0.7)

    fun evalUnit(type: UnitType, base: Double, scaleFactor: Double = 500.0): Double {
        val needed = needed(type)
        if (needed < 0) return 0.0
        val eval = (base - fastsig(needed / 6.0) * 0.1) *
                (1 + max(0.0, fastsig(((FTTBot.frameCount - EnemyInfo.lastNewEnemyFrame) / scaleFactor - UnitQuery.myUnits.count { it.type == type }) * 0.01)) * 0.3) *
                (if ((type.allRequiredUnits() - type - UnitQuery.myUnits.map { it.type }).isEmpty()) 1.0 else 0.7)
        return MathUtils.clamp(eval, 0.0, 1.0)
    }

    private val enemySims by LazyOnFrame {
        (EnemyInfo.seenUnits + UnitQuery.enemyUnits).filter { it is MobileUnit || it is Attacker }.map(SimUnit.Companion::of).map { it.position = null; it }
    }

    val moreLurkersUtility by LazyOnFrame {
        evalUnit(UnitType.Zerg_Lurker, 0.6)
    }

    val moreHydrasUtility by LazyOnFrame { evalUnit(UnitType.Zerg_Hydralisk, 0.7) }

    val moreMutasUtility by LazyOnFrame {
        evalUnit(UnitType.Zerg_Mutalisk, 0.7)
    }

    val moreUltrasUtility by LazyOnFrame {
        evalUnit(UnitType.Zerg_Ultralisk, 0.65)
    }

    val moreLingsUtility by LazyOnFrame {
        evalUnit(UnitType.Zerg_Zergling, 0.71, 600.0)
    }

    val moreGasUtility by LazyOnFrame {
        if (MyInfo.myBases.none { base -> UnitQuery.geysers.any { it.getDistance(base) < 300 } })
            0.0
        else
            min(1.0, FTTBot.self.minerals() / (UnitQuery.myUnits.count { it is GasMiningFacility } * 30.0 + FTTBot.self.gas() + 200.0))
    }

    val moreSupplyUtility by LazyOnFrame {
        val freeSupply = FTTBot.self.supplyTotal() - FTTBot.self.supplyUsed()
        if (MyInfo.pendingSupply() + FTTBot.self.supplyTotal() >= 400)
            0.0
        else
            min(1.0, min(UnitQuery.my<ResourceDepot>().size * 8.0, FTTBot.self.minerals() / 13.0) / max(1, MyInfo.pendingSupply() + freeSupply))
    }
}