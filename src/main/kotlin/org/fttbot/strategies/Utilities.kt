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
import org.fttbot.info.inRadius
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.GasMiningFacility
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker
import kotlin.math.max
import kotlin.math.min

object Utilities {
    val expansionUtility by LazyOnFrame {
        min(1.0, workerUtilization * (1.15 - fastsig(MyInfo.myBases.count() * 0.05)))
    }

    val workerUtilization by LazyOnFrame {
        min(1.0, (UnitQuery.myWorkers.size.toDouble() + UnitQuery.myEggs.count { it.buildType.isWorker }) / 1.6 / (0.1 + MyInfo.myBases.sumBy {
            it as PlayerUnit; UnitQuery.minerals.inRadius(it, 300).count() + UnitQuery.myBuildings.count { it is GasMiningFacility } * 3
        }))
    }

    val mineralsUtilization by LazyOnFrame {
        min(1.0, FTTBot.self.minerals() / 2000.0)
    }

    val gasUtilization by LazyOnFrame {
        min(1.0, FTTBot.self.gas() / 1000.0)
    }

    val moreTrainersUtility by LazyOnFrame {
        MathUtils.clamp((MyInfo.myBases.size * workerUtilization + 1.0) / (UnitQuery.myBases.size + 1.0), 0.0, 1.0)
    }

    val moreWorkersUtility by LazyOnFrame {
        (1.0 - fastsig(workerUtilization * 0.8)) * max(1.0 - mineralsUtilization * 0.6, 1.0 - gasUtilization * 0.6)
    }

    fun needed(type: UnitType) = CombatEval.minAmountOfAdditionalsForProbability(UnitQuery.myUnits.filter { it is MobileUnit && it !is Worker }.map(SimUnit.Companion::of)
            .map { it.position = null; it }, of(type),
            enemySims)

    fun evalUnit(type: UnitType, base: Double, scaleFactor: Double = 30000.0): Double {
        val needed = needed(type)
        val eval = (if (needed <= 0) base else (if (FTTBot.self.canMake(type)) 1.0 else base) - fastsig(needed / 15.0) * 0.8) *
                (1 + (FTTBot.frameCount - EnemyInfo.lastNewEnemyFrame) / scaleFactor)
        return MathUtils.clamp(eval, 0.0, 1.0)
    }

    private val enemySims by LazyOnFrame {
        (EnemyInfo.seenUnits + UnitQuery.enemyUnits).filter { it is MobileUnit }.map(SimUnit.Companion::of).map { it.position = null; it }
    }

    val moreLurkersUtility by LazyOnFrame {
        evalUnit(UnitType.Zerg_Lurker, 0.55)
    }

    val moreHydrasUtility by LazyOnFrame { evalUnit(UnitType.Zerg_Hydralisk, 0.5) }

    val moreMutasUtility by LazyOnFrame {
        evalUnit(UnitType.Zerg_Mutalisk, 0.55)
    }

    val moreUltrasUtility by LazyOnFrame {
        evalUnit(UnitType.Zerg_Ultralisk, 0.2)
    }

    val moreLingsUtility by LazyOnFrame {
        evalUnit(UnitType.Zerg_Zergling, 0.56, 35000.0)
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