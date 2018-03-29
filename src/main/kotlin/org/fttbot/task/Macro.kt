package org.fttbot.task

import bwapi4j.org.apache.commons.lang3.mutable.MutableInt
import org.fttbot.*
import org.fttbot.info.UnitQuery
import org.fttbot.task.Production.build
import org.fttbot.task.Production.buildGas
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.unit.PlayerUnit

object Macro {
    fun findBestExpansionPosition(): TilePosition? {
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
        return targetBase?.location
    }

    fun buildExpansion(): Node {
        var expansionPosition: TilePosition? = null
        return Sequence(
                Inline("Find location to expand") {
                    expansionPosition = findBestExpansionPosition() ?: return@Inline NodeStatus.FAILED
                    NodeStatus.SUCCEEDED
                },
                Delegate {
                    build(FTTConfig.BASE) {
                        expansionPosition!!
                    }
                }
        )
    }

    fun considerGas(): Node = Fallback(
            Sequence(
                    Condition("Not enough gas mines?") {
                        (Board.pendingUnits.sumBy { it.gasPrice() } > 0 ||
                                Board.resources.gas < 0) && (FTTBot.self.minerals() / 3 > FTTBot.self.gas())
                                && Info.myBases.any { base -> base as PlayerUnit; UnitQuery.geysers.any { it.getDistance(base) < 300 } }
                    },
                    Delegate {
                        buildGas()
                    }),
            Sleep
    )
}