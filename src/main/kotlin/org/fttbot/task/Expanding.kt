package org.fttbot.task

import org.fttbot.FTTBot
import org.fttbot.FTTConfig
import org.fttbot.info.EnemyInfo
import org.fttbot.info.MyInfo
import org.fttbot.strategies.Utilities
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.org.apache.commons.lang3.mutable.MutableInt
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.PlayerUnit
import java.util.*

class Expanding : Task() {
    private val prng = SplittableRandom()
    private val construct = ConstructBuilding(UnitType.Zerg_Hatchery)

    override val utility: Double
        get() = Utilities.expansionUtility

    override fun reset() {
        construct.at = null
        super.reset()
    }

    override fun processInternal(): TaskStatus {
        if (construct.at == null) {
            val bestExpansionPosition = findBestExpansionPosition() ?: return TaskStatus.FAILED

            construct.at = bestExpansionPosition
        }
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
        private val expand: List<Task> = listOf(Expanding().neverFail().repeat())

        override fun invoke(): List<Task> = expand
    }
}