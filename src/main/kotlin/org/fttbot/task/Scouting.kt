package org.fttbot.task

import com.badlogic.gdx.math.Vector2
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.task.Actions.flee
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.Overlord
import java.util.*

object Scouting {
    private val rnd = SplittableRandom()
    fun scout(scout: MobileUnit, target: TilePosition): Node {
        return sequence(
                Inline("Mark scout location") {
                    Board.pendingLocations.add(target)
                    NodeStatus.SUCCEEDED
                },
                Actions.reach(scout, target.toPosition(), 50)
        )
    }

    fun scout(): Node {
        var scouts: List<MobileUnit> = emptyList()
        return fallback(
                sequence(
                        Inline("determine scout") {
                            scouts = Board.resources.units.filterIsInstance(Overlord::class.java)
                            if (scouts.isEmpty())
                                NodeStatus.RUNNING
                            else
                                NodeStatus.SUCCEEDED
                        },
                        DispatchParallel("Scouting", { scouts }) { s ->
                            val reachBoard = ReachBoard(tolerance = 8)
                            fallback(
                                    sequence(
                                            Condition("Damaged beyond repair?") { s.hitPoints < s.maxHitPoints() / 2 },
                                            Inline("Return home") {
                                                val force = Vector2()
                                                Potential.addThreatRepulsion(force, s)
                                                Potential.addWallAttraction(force, s, 0.5f)
                                                Potential.addSafeAreaAttractionDirect(force, s, 1.3f)
                                                force.nor()
                                                reachBoard.position = s.position + force.scl(s.topSpeed.toFloat() * 20).toPosition()
                                                NodeStatus.SUCCEEDED
                                            },
                                            Inline("Move move move") {
                                                s.move(reachBoard.position)
                                                NodeStatus.SUCCEEDED
                                            }
                                    ),
                                    flee(s),
                                    sequence(
                                            Condition("Unexplored start locations?") { org.fttbot.FTTBot.game.bwMap.startPositions.any { !org.fttbot.FTTBot.game.bwMap.isExplored(it) } },
                                            Delegate {
                                                val unexplored = org.fttbot.FTTBot.game.bwMap.startPositions
                                                        .filter { !org.fttbot.FTTBot.game.bwMap.isExplored(it) } - Board.pendingLocations
                                                scout(s, unexplored.minBy { s.getDistance(it.toPosition()) }
                                                        ?: return@Delegate Fail)
                                            }
                                    ),
                                    sequence(
                                            Condition("Unexplored base locations?") {
                                                FTTBot.bwem.bases.any { !FTTBot.game.bwMap.isExplored(it.location) }
                                            },
                                            Delegate {
                                                val unexplored = FTTBot.bwem.bases
                                                        .filter { !org.fttbot.FTTBot.game.bwMap.isExplored(it.location) }.map { it.location } - Board.pendingLocations
                                                scout(s, unexplored.minBy { s.getDistance(it.toPosition()) }
                                                        ?: return@Delegate Fail)
                                            }
                                    ),
                                    sequence(
                                            Condition("Any 'hiddenAttack' bases?") {
                                                FTTBot.bwem.bases.any { !org.fttbot.FTTBot.game.bwMap.isVisible(it.location) }
                                            },
                                            Delegate {
                                                val hiddenBases = FTTBot.bwem.bases
                                                        .filter { !org.fttbot.FTTBot.game.bwMap.isVisible(it.location) }
                                                        .map { it.location } - Board.pendingLocations
                                                if (hiddenBases.isEmpty())
                                                    Fail
                                                else
                                                    scout(s, hiddenBases[rnd.nextInt(hiddenBases.size)])
                                            }
                                    ),
                                    Sleep
                            )
                        }
                ), Sleep)
    }

}