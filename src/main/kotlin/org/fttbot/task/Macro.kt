package org.fttbot.task

import bwapi4j.org.apache.commons.lang3.mutable.MutableInt
import org.fttbot.*
import org.fttbot.task.Production.build
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.PlayerUnit

object Macro {
    fun findBestExpansionPosition(): Position? {
        val existingBases = Info.myBases
        val candidates = FTTBot.bwem.bases.filter { base ->
            existingBases.none { (it as PlayerUnit).getDistance(base.center) < 200 } &&
                    FTTBot.game.canBuildHere(base.location, FTTConfig.BASE)
        }
        // TODO Determine if there's a "natural" that could be defended together with the main
        val targetBase =
                candidates.minBy { base ->
                    existingBases.map {
                        val result = MutableInt()
                        FTTBot.bwem.getPath((it as PlayerUnit).position, base.center, result)
                        result.toInt()
                    }.min() ?: 0
                }
        return targetBase?.center
    }

    fun buildExpansion(): Node {
        var expansionPosition: Position? = null
        return Sequence(
                Inline {
                    expansionPosition = expansionPosition ?: findBestExpansionPosition() ?: return@Inline NodeStatus.FAILED
                    NodeStatus.SUCCEEDED
                },
                MDelegate {
                    build(FTTConfig.BASE) {
                        expansionPosition!!.toTilePosition()
                    }
                }
        )
    }
}