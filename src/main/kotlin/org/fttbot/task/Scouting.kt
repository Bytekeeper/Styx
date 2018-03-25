package org.fttbot.task

import org.fttbot.*
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Overlord
import org.openbw.bwapi4j.unit.PlayerUnit
import java.util.*

object Scouting {
    private val rnd = SplittableRandom()
    fun scout(scout: MobileUnit, target: Position): Node {
        return CompoundActions.reach(scout, target, 200)
    }

    fun scout(): Node {
        var scouts: List<PlayerUnit> = emptyList()
        return Sequence(
                Inline("determine scout") {
                    scouts = Board.resources.units.filter { it is Overlord }
                    if (scouts.isEmpty())
                        NodeStatus.RUNNING
                    else
                        NodeStatus.SUCCEEDED
                },
                DispatchParallel({ scouts }) {
                    scout(it as MobileUnit, FTTBot.game.bwMap.startPositions[rnd.nextInt(FTTBot.game.bwMap.startPositions.size)].toPosition())
                }
        )
    }
}