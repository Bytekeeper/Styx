package org.fttbot.task

import bwem.ChokePoint
import org.fttbot.*
import org.fttbot.CompoundActions.reach
import org.fttbot.estimation.SimUnit
import org.fttbot.info.UnitQuery
import org.fttbot.info.inRadius
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.WalkPosition
import org.openbw.bwapi4j.unit.Larva
import org.openbw.bwapi4j.unit.MobileUnit
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker
import kotlin.math.max

object Combat {
    fun attack(units: List<PlayerUnit>, targets: List<PlayerUnit>): Node {
        return Sequence(MMapAll({ units }, "attack") { unit ->
            val simUnit = SimUnit.of(unit)
            var bestTarget: PlayerUnit? = null

            Sequence(
                    Inline {
                        bestTarget = targets.minBy {
                            (it.hitPoints + it.shields) / max(simUnit.damagePerFrameTo(SimUnit.of(it)), 0.01) +
                                    (if (it is Larva) 5000 else 0) +
                                    it.getDistance(unit)
                        }
                        if (bestTarget == null)
                            NodeStatus.FAILED
                        else
                            NodeStatus.SUCCEEDED
                    },
                    Delegate { Attack(unit, bestTarget!!) }
            )
        }, Sleep)
    }

    fun moveToStandOffPosition(units: List<MobileUnit>, targetPosition: Position): Node {
        if (units.isEmpty()) return Success
        val path = FTTBot.bwem.getPath(FTTBot.self.startLocation.toPosition(), targetPosition)
        if (path.isEmpty) return Fail
        val cpIndex = path.indexOfFirst { cp ->
            !UnitQuery.enemyUnits.inRadius(cp.center.toPosition(), 300).isEmpty()
        }
        if (cpIndex <= 0)
            return Fail
        val cpCenter = path[cpIndex - 1].center
        val actualTarget =
                if (cpIndex == 1)
                    cpCenter
                else {
                    val adjacentAreas = path[cpIndex - 2].areas
                    val aa = adjacentAreas.first.walkPositionWithHighestAltitude
                    val ab = adjacentAreas.second.walkPositionWithHighestAltitude
                    if (aa.toPosition().getDistance(targetPosition) < ab.toPosition().getDistance(targetPosition))
                        cpCenter.add(aa).divide(WalkPosition(2, 2))
                    else
                        cpCenter.add(ab).divide(WalkPosition(2, 2))
                }
        return MMapAll({ units }, "moveToStandOff") {
            reach(it, actualTarget.toPosition(), 96)
        }
    }

    fun defendWithWorkers(units: List<Worker>, targets: List<PlayerUnit>): Node {
        return attack(units, targets)
    }

    fun defendPosition(units: List<MobileUnit>, position: Position, against: Position): Node {
        val defensePosition = findGoodDefenseChokePoint(position, against)?.center?.toPosition() ?: return Fail
        var enemies = emptyList<PlayerUnit>()
        return Fallback(
                Sequence(
                        Inline {
                            enemies = UnitQuery.enemyUnits.inRadius(defensePosition, 300)
                            if (enemies.isEmpty()) NodeStatus.FAILED else NodeStatus.SUCCEEDED
                        },
                        MDelegate("Fending off") {
                            attack(units, enemies)
                        }
                ),
                MMapAll({ units }, "defendPosition") {
                    reach(it, defensePosition, 64)
                }
        )
    }

    fun findGoodDefenseChokePoint(near: Position, against: Position): ChokePoint? {
        val consideredCPs = FTTBot.bwem.getPath(near, against)
                .filter { !it.isBlocked }
                .takeWhile { near.getDistance(it.center.toPosition()) < 1000 }
        val bestCP = consideredCPs
                .withIndex()
                .minBy { it.value.geometry.size } ?: return null
        return bestCP.value
    }


}