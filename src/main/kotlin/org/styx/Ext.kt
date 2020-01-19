package org.styx

import bwapi.*
import org.bk.ass.manage.GMS
import org.styx.Styx.resources
import kotlin.math.max
import kotlin.math.min

fun Iterable<Double>.min(default: Double): Double = min() ?: default
fun Iterable<Int>.min(default: Int): Int = min() ?: default


fun WalkPosition.middlePosition() = toPosition() + Position(4, 4)

operator fun TilePosition.div(factor: Int) = divide(factor)

fun TilePosition.adjacentTiles(maxDistance: Int): Sequence<TilePosition> =
        (max(0, x - maxDistance)..min(Styx.game.mapWidth(), x + maxDistance)).asSequence()
                .flatMap { x ->
                    (max(0, y - maxDistance)..min(Styx.game.mapHeight(), y + maxDistance)).asSequence()
                            .map { y -> TilePosition(x, y) }
                }

fun TilePosition.closestTo(position: Position) =
        arrayOf(toPosition(), toPosition() + Position(31, 0), toPosition() + Position(31, 31), toPosition() + Position(0, 31))
                .minBy { it.getDistance(position) }!!

val TilePosition.altitude get() = Styx.game.getGroundHeight(this)
fun TilePosition.middlePosition() = toPosition() + Position(16, 16)

fun Player.canUpgrade(upgradeType: UpgradeType): Boolean {
    val currentLevel = getUpgradeLevel(upgradeType)
    return !isUpgrading(upgradeType)
            && currentLevel < getMaxUpgradeLevel(upgradeType)
            && resources.availableGMS.canAfford(GMS.upgradeCost(upgradeType, currentLevel + 1))
            && Styx.units.myCompleted(upgradeType.whatsRequired(currentLevel + 1)).isNotEmpty()
            && Styx.units.myCompleted(upgradeType.whatUpgrades()).isNotEmpty()
}


operator fun GMS.minus(value: GMS) = subtract(value)
operator fun GMS.plus(value: GMS) = add(value)
operator fun GMS.times(amount: Int) = GMS(gas * amount, minerals * amount, supply * amount)
