package org.fttbot.task

import bwapi4j.org.apache.commons.lang3.mutable.MutableInt
import org.fttbot.*
import org.fttbot.Fallback.Companion.fallback
import org.fttbot.Sequence.Companion.sequence
import org.fttbot.info.*
import org.fttbot.task.Actions.flee
import org.fttbot.task.Actions.reachSafely
import org.fttbot.task.Production.build
import org.fttbot.task.Production.buildGas
import org.fttbot.task.Production.produceSupply
import org.openbw.bwapi4j.TilePosition
import org.openbw.bwapi4j.unit.PlayerUnit
import java.util.*
import kotlin.math.max
import kotlin.math.min

object Macro {
    val prng = SplittableRandom()
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
                        val distance = MutableInt()
                        FTTBot.bwem.getPath(it.position, base.center, distance)
                        distance.toInt()
                    }.min() ?: 0))
            }.minBy { it.second }?.first
        return targetBase?.location
    }

    fun buildExpansion(): Node {
        var expansionPosition: TilePosition? = null
        return sequence(
                Inline("Find location to expand") {
                    expansionPosition = findBestExpansionPosition()
                            ?: return@Inline NodeStatus.FAILED
                    NodeStatus.SUCCEEDED
                },
                fallback(Condition("Safe to build there?") {
                    val buildArea = FTTBot.bwem.getArea(expansionPosition!!.toWalkPosition())
                    !EnemyInfo.occupiedAreas.keys.contains(buildArea) || MyInfo.occupiedAreas.keys.contains(buildArea)
                }, Sleep),
                Retry(100,
                        Delegate {
                            build(FTTConfig.BASE, true) {
                                expansionPosition!!
                            }
                        }
                )
        )
    }

    fun considerGas(): Node =
            Repeat(child = fallback(
                    sequence(
                            Condition("Not enough gas mines?") {
                                (Board.pendingUnits.sumBy { it.gasPrice() } > 0 ||
                                        Board.resources.gas < 0) && (FTTBot.self.minerals() / 3 > FTTBot.self.gas())
                                        && MyInfo.myBases.any { base -> base as PlayerUnit; UnitQuery.geysers.any { it.getDistance(base) < 300 } }
                            }, Delegate {
                        buildGas()
                    }),
                    Sleep
            )
            )

    fun moveSurplusWorkers() = DispatchParallel("Consider worker transfer", { MyInfo.myBases.filter { it.isReadyForResources } }) { base ->
        base as PlayerUnit
        val minerals = UnitQuery.minerals.count { it.getDistance(base) < 300 }
        val relevantWorkers = UnitQuery.myWorkers.filter { it.getDistance(base) < 300 && (it.isGatheringGas || it.isGatheringMinerals) }
        val workerDelta = relevantWorkers.size - minerals * 2
        if ((minerals < 3 && workerDelta > 0) || workerDelta > 5) {
            val targetBase = MyInfo.myBases.filter { it.isReadyForResources }.minBy { targetBase ->
                targetBase as PlayerUnit
                UnitQuery.myWorkers.count { it.getDistance(targetBase) < 300 } - UnitQuery.minerals.count { it.getDistance(targetBase) < 300 } * 2
            } ?: return@DispatchParallel Sleep
            Delegate {
                DispatchParallel("Transfer workers", { relevantWorkers.filter { Board.resources.units.contains(it) }.take(workerDelta) }) {
                    fallback(sequence(
                            ReserveUnit(it),
                            fallback(
                                    reachSafely(it, (targetBase as PlayerUnit).position, 100),
                                    flee(it)
                            )
                    ), Success)
                }
            }
        } else
            Success
    }

    fun preventSupplyBlock() = fallback(sequence(
            Condition("Enough minerals to prebuild supply?") {
                val freeSupply = FTTBot.self.supplyTotal() - FTTBot.self.supplyUsed()
                MyInfo.pendingSupply() + FTTBot.self.supplyTotal() < 400 &&
                        max(0, MyInfo.pendingSupply() + freeSupply) < min(UnitQuery.myBases.size * 6, FTTBot.self.minerals() / 90)
            },
            produceSupply(),
            Sleep),
            Sleep
    )

    fun considerExpansion() = Repeat(child = fallback(
            Delegate { Macro.buildExpansion() } onlyIf Condition("should build exe") {
                UnitQuery.myWorkers.size >= MyInfo.myBases.sumBy { it as PlayerUnit; UnitQuery.minerals.inRadius(it, 300).count() + 2 }
            },
            Sleep)
    )

}