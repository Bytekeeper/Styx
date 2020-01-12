package org.styx.task

import bwapi.UnitType
import bwapi.UpgradeType
import org.bk.ass.bt.*
import org.styx.*
import org.styx.Styx.diag
import org.styx.Styx.economy
import org.styx.Styx.relevantGameResults
import org.styx.Styx.resources
import org.styx.macro.*
import java.lang.Math.random
import kotlin.math.ln
import kotlin.math.sqrt

open class Strat(
        name: String,
        private vararg val nodes: TreeNode) : BehaviorTree() {
    private var utility = 0.0
    override fun getUtility(): Double = utility

    init {
        withName(name)
    }

    override fun getRoot(): TreeNode = Parallel(Parallel.Policy.SEQUENCE,
            LambdaNode {
                if (status == NodeStatus.INITIAL) {
                    println("Selected strategy: $name with bound $utility")
                    diag.log("Selected strategy: $name with bound $utility")
                    Styx.strategy = name
                }
                NodeStatus.SUCCESS
            },
            *nodes)

    override fun init() {
        super.init()
        val n = relevantGameResults.amount
        val j = relevantGameResults.filteredByStrategy(name)
        val nj = j.amount
        val xj = j.score
        utility = xj + sqrt(1.0 * ln(n.toDouble() + 1) / (nj + 1)) + random() * 0.0000001
        println("Info for strategy $name: winrate: $xj, tried: $nj/$n; bound: $utility")
        diag.log("Info for strategy $name: winrate: $xj, tried: $nj/$n; bound: $utility")
    }

    override fun reset() {
        error("ERROR!")
    }
}

object TenHatch : Strat("10Hatch",
        Get(9, UnitType.Zerg_Drone),
        ExtractorTrick(),
        Expand(),
        Build(UnitType.Zerg_Spawning_Pool),
        Repeat(Get(9, UnitType.Zerg_Drone)),
        Get(2, UnitType.Zerg_Overlord),
        ExtractorTrick(),
        Build(UnitType.Zerg_Hatchery),
        Get(8, UnitType.Zerg_Zergling),
        ensureSupply(),
        pumpLings()
)

object TwoHatchMuta : Strat("2HatchMuta",
        twelveHatchBasic,
        Get(12, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Extractor),
        Repeat(Get(8, UnitType.Zerg_Zergling)),
        Repeat(Get(12, UnitType.Zerg_Drone)),
        Morph(UnitType.Zerg_Lair),
        Get(3, UnitType.Zerg_Overlord),
        Upgrade(lingSpeedUpgrade, 1),
        Get(20, UnitType.Zerg_Drone),
        Repeat(Get(1, UnitType.Zerg_Spire)),
        Get(22, UnitType.Zerg_Drone),
        Expand(),
        Build(UnitType.Zerg_Extractor),
        Repeat(Get(22, UnitType.Zerg_Drone)),
        Get(5, UnitType.Zerg_Overlord),
        ensureSupply(),
        Repeat(Repeat.Policy.SELECTOR,
                Sequence(
                        Condition { resources.availableGMS.gas < UnitType.Zerg_Mutalisk.gasPrice() },
                        pumpLings()
                )
        ),
        pumpMutas(),
        Expand()
)

object ThreeHatchMuta : Strat("3HatchMuta",
        twelveHatchBasic,
        Get(13, UnitType.Zerg_Drone),
        Expand(),
        Build(UnitType.Zerg_Extractor),
        Get(2, UnitType.Zerg_Zergling),
        Get(15, UnitType.Zerg_Drone),
        Get(3, UnitType.Zerg_Overlord),
        Morph(UnitType.Zerg_Lair),
        Get(20, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Extractor),
        Upgrade(lingSpeedUpgrade, 1),
        Get(23, UnitType.Zerg_Drone),
        Repeat(Get(1, UnitType.Zerg_Spire)),
        Get(23, UnitType.Zerg_Drone),
        Get(4, UnitType.Zerg_Overlord),
        Repeat(Get(25, UnitType.Zerg_Drone)),
        Get(12, UnitType.Zerg_Zergling),
        Expand(),
        Get(6, UnitType.Zerg_Overlord),
        ensureSupply(),
        Repeat(Repeat.Policy.SELECTOR,
                Sequence(
                        Condition { resources.availableGMS.gas < UnitType.Zerg_Mutalisk.gasPrice() },
                        pumpLings()
                )
        ),
        pumpMutas()
)

object TwoHatchHydra : Strat("2HatchHydra",
        twelveHatchBasic,
        Build(UnitType.Zerg_Extractor),
        Get(13, UnitType.Zerg_Drone),
//        Repeat(Get(8, UnitType.Zerg_Zergling)),
        Build(UnitType.Zerg_Hydralisk_Den),
        Repeat(Get(16, UnitType.Zerg_Drone)),
        Train(UnitType.Zerg_Overlord),
        Upgrade(hydraRangeUpgrade, 1),
        Get(12, UnitType.Zerg_Hydralisk),
        Upgrade(hydraSpeedUpgrade, 1),
        ensureSupply(),
        Repeat(Repeat.Policy.SELECTOR,
                Sequence(
                        Condition { resources.availableGMS.minerals > 600 },
                        Build(UnitType.Zerg_Hatchery)
                )
        ),
        Repeat(Repeat.Policy.SELECTOR,
                Sequence(
                        Condition { resources.availableGMS.gas < UnitType.Zerg_Hydralisk.gasPrice() },
                        pumpLings()
                )
        ),
        pumpHydras()
)

object ThirteenPoolMuta : Strat("13PoolMuta",
        Get(9, UnitType.Zerg_Drone),
        Get(2, UnitType.Zerg_Overlord),
        Get(12, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Get(12, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Extractor),
        Get(12, UnitType.Zerg_Drone),
        Expand(),
        Morph(UnitType.Zerg_Lair),
        Repeat(Get(4, UnitType.Zerg_Zergling)),
        Get(14, UnitType.Zerg_Drone),
        Get(3, UnitType.Zerg_Overlord),
        Repeat(Get(15, UnitType.Zerg_Drone)),
        Build(UnitType.Zerg_Spire),
        Get(1, UnitType.Zerg_Creep_Colony),
        Morph(UnitType.Zerg_Sunken_Colony),
        Build(UnitType.Zerg_Extractor),
        Repeat(Get(18, UnitType.Zerg_Drone)),
        Get(5, UnitType.Zerg_Overlord),
        ensureSupply(),
        pumpMutas()
)

object Nine734 : Strat("9734",
        overPoolBasic,
        Get(2, UnitType.Zerg_Zergling),
        Get(12, UnitType.Zerg_Drone),
        Expand(),
        Build(UnitType.Zerg_Extractor),
        Get(17, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Hydralisk_Den),
        Train(UnitType.Zerg_Overlord),
        Get(18, UnitType.Zerg_Drone),
        Upgrade(hydraSpeedUpgrade, 1),
        Repeat(Get(4, UnitType.Zerg_Zergling)),
        Repeat(Get(22, UnitType.Zerg_Drone)),
        ensureSupply(),
        Get(5, UnitType.Zerg_Hydralisk),
        Upgrade(hydraRangeUpgrade, 1),
        Expand(),
        Repeat(Get(10, UnitType.Zerg_Hydralisk)),
        Repeat(Get(29, UnitType.Zerg_Drone)),
        Build(UnitType.Zerg_Hatchery),
        Build(UnitType.Zerg_Extractor),
//        Morph(UnitType.Zerg_Lair),
        Get(20, UnitType.Zerg_Hydralisk),
        Build(UnitType.Zerg_Evolution_Chamber),
        Get(23, UnitType.Zerg_Hydralisk),
        Build(UnitType.Zerg_Hatchery),
        Upgrade(UpgradeType.Zerg_Missile_Attacks, 1),
        Upgrade(UpgradeType.Metabolic_Boost, 1),
        Repeat(Repeat.Policy.SELECTOR,
                Sequence(
                        Condition { resources.availableGMS.gas < UnitType.Zerg_Hydralisk.gasPrice() },
                        pumpLings()
                )
        ),
        pumpHydras()
)

object NinePoolCheese : Strat("9 Pool Cheese",
        ninePoolBasic,
        Train(UnitType.Zerg_Overlord),
        ensureSupply(),
        { Train(UnitType.Zerg_Zergling) }.repeat(3),
        haveBasicZerglingSquad(),
        Repeat(Get(7, UnitType.Zerg_Drone)),
        pumpLings(),
        Get(2, UnitType.Zerg_Hatchery),
        Build(UnitType.Zerg_Extractor),
        Upgrade(lingSpeedUpgrade, 1),
        Gathering(true)
)

val ninePoolBasic = Parallel(Parallel.Policy.SEQUENCE,
        Get(9, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool)
)

val overPoolBasic = Parallel(Parallel.Policy.SEQUENCE,
        Get(9, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Build(UnitType.Zerg_Spawning_Pool),
        Get(11, UnitType.Zerg_Drone),
        Expand()
)

val twelveHatchBasic = Parallel(Parallel.Policy.SEQUENCE,
        Get(9, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Get(12, UnitType.Zerg_Drone),
        Expand(),
        Build(UnitType.Zerg_Spawning_Pool)
)


private fun pumpLings() = Repeat(Get(200, UnitType.Zerg_Zergling, true))
private fun pumpHydras() = Repeat(Get(200, UnitType.Zerg_Hydralisk, true))
private fun pumpMutas() = Repeat(Get(200, UnitType.Zerg_Mutalisk, true))
private fun haveBasicZerglingSquad() = Repeat(Get(6, UnitType.Zerg_Zergling))
private fun ensureSupply() =
        Repeat(
                Sequence(
                        Repeat(Repeat.Policy.SELECTOR, Condition {
                            economy.supplyWithPlanned < 4 ||
                                    economy.supplyWithPlanned < 16 && resources.availableGMS.minerals > 400
                        }),
                        Train(UnitType.Zerg_Overlord)
                )
        )