package org.fttbot.task

import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.task.Actions.flee
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Overlord
import org.openbw.bwapi4j.unit.PlayerUnit
import java.util.*

object Scouting {
    private val rnd = SplittableRandom()
    fun scout(scout: MobileUnit, target: Position): Node<Any, Any> {
        return Actions.reach(scout, target, 200)
    }

    fun scout(): Node<Any, Any> {
        var scouts: List<PlayerUnit> = emptyList()
        return fallback(
                sequence(
                        Inline("determine scout") {
                            scouts = Board.resources.units.filter { it is Overlord }
                            if (scouts.isEmpty())
                                NodeStatus.RUNNING
                            else
                                NodeStatus.SUCCEEDED
                        },
                        DispatchParallel({ scouts }) { s ->
                            s as MobileUnit
                            fallback(
                                    flee(s),
                                    sequence(
                                            Condition("Unexplored start locations?") { org.fttbot.FTTBot.game.bwMap.startPositions.any { !org.fttbot.FTTBot.game.bwMap.isExplored(it) } },
                                            Delegate {
                                                val unexplored = org.fttbot.FTTBot.game.bwMap.startPositions.filter { !org.fttbot.FTTBot.game.bwMap.isExplored(it) }
                                                scout(s, unexplored[rnd.nextInt(unexplored.size)].toPosition())
                                            }
                                    ),
                                    sequence(
                                            Condition("Unexplored base locations?") {
                                                FTTBot.bwem.bases.any { !FTTBot.game.bwMap.isExplored(it.location) }
                                            },
                                            Delegate {
                                                val unexplored = FTTBot.bwem.bases.filter { !org.fttbot.FTTBot.game.bwMap.isExplored(it.location) }
                                                scout(s, unexplored[rnd.nextInt(unexplored.size)].location.toPosition())
                                            }
                                    ),
                                    sequence(
                                            Condition("Any 'hidden' bases?") {
                                                FTTBot.bwem.bases.any { !org.fttbot.FTTBot.game.bwMap.isVisible(it.location) }
                                            },
                                            Delegate {
                                                val hiddenBases = FTTBot.bwem.bases.filter { !org.fttbot.FTTBot.game.bwMap.isVisible(it.location) }
                                                scout(s, hiddenBases[rnd.nextInt(hiddenBases.size)].location.toPosition())
                                            }
                                    ),
                                    Sleep
                            )
                        }
                ), Sleep)
    }

}