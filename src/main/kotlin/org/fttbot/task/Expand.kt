package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.FTTConfig
import org.fttbot.Locked
import org.fttbot.info.EnemyInfo
import org.fttbot.info.MyInfo
import org.fttbot.info.UnitQuery
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.org.apache.commons.lang3.mutable.MutableInt
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.Building
import org.openbw.bwapi4j.unit.PlayerUnit
import java.util.*

class Expand(val utilityProvider: () -> Double = { 1.0 }) : Task() {
    private val prng = SplittableRandom()

    override val utility: Double
        get() = utilityProvider()

    private val construct by LazyTask {
        Build(UnitType.Zerg_Hatchery)
    }
    private var expansionPosition = Locked<TilePosition>(this) { UnitQuery.ownedUnits.inRadius(it.toPosition(), 128).none { it is Building } }

    override fun processInternal(): TaskStatus {
        val targetPosition = expansionPosition.compute {
            val candidate = findBestExpansionPosition() ?: return@compute null
            if (it(candidate)) candidate else null
        } ?: return TaskStatus.FAILED
        construct.at = targetPosition
        return construct.process()
    }

    fun findBestExpansionPosition(): TilePosition? {
        val existingBases = MyInfo.myBases
        val candidates = FTTBot.bwem.bases.filter { base ->
            existingBases.none { (it as PlayerUnit).getDistance(base.center) < 200 } &&
                    FTTBot.game.canBuildHere(base.location, FTTConfig.BASE)
        }
        // TODO Determine if there's a "natural" that could be defended together with the main
        val targetBase = if (existingBases.isEmpty()) candidates[prng.nextInt(candidates.size)] else
            candidates.mapNotNull { base ->
                val minDist = existingBases.mapNotNull {
                    val result = MutableInt()
                    FTTBot.bwem.getPath((it as PlayerUnit).position, base.center, result)
                    val length = result.toInt()
                    if (length < 0) null else length
                }.min()
                if (minDist == null) null else
                    base to (minDist - (EnemyInfo.enemyBases.map {
                        try {
                            val distance = MutableInt()
                            FTTBot.bwem.getPath(it.position, base.center, distance)
                            distance.toInt()
                        } catch (e: IllegalStateException) {
                            Int.MAX_VALUE
                        }
                    }.min() ?: 0))
            }.minBy { it.second }?.first
        return targetBase?.location
    }


    companion object : TaskProvider {
        private val expand: List<Task> = listOf(Expand { Utilities.expansionUtility }.nvr())

        override fun invoke(): List<Task> = expand

    }
}