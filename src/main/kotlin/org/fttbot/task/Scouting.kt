package org.fttbot.task

import org.fttbot.*
import org.fttbot.info.UnitQuery
import org.fttbot.info.canAttack
import org.fttbot.task.Actions.flee
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Overlord
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Unit
import java.util.*

object Scouting {
    private val rnd = SplittableRandom()
    fun scout(scout: MobileUnit, target: Position): Node<Any, Any> {
        return Actions.reach(scout, target, 200)
    }

    fun scout(): Node<Any, Any> {
        var scouts: List<PlayerUnit> = emptyList()
        return Fallback(
                Sequence(
                        Inline("determine scout") {
                            scouts = Board.resources.units.filter { it is Overlord }
                            if (scouts.isEmpty())
                                NodeStatus.RUNNING
                            else
                                NodeStatus.SUCCEEDED
                        },
                        DispatchParallel({ scouts }) {
                            s ->
                            s as MobileUnit
                            Fallback(
                                    flee(s),
                                scout(s, FTTBot.game.bwMap.startPositions[rnd.nextInt(FTTBot.game.bwMap.startPositions.size)].toPosition())
                            )
                        }
                ), Sleep)
    }

}