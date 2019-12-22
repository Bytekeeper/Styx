package org.styx.task

import bwapi.UnitType
import bwapi.UpgradeType
import org.styx.*
import org.styx.Par.Companion.repeat
import org.styx.Styx.diag
import org.styx.Styx.economy
import org.styx.Styx.relevantGameResults
import org.styx.Styx.resources
import org.styx.Styx.units
import org.styx.macro.*
import java.lang.Math.random
import kotlin.math.ln
import kotlin.math.sqrt

open class Strat(
        name: String,
        private vararg val nodes: SimpleNode) : BehaviorTree(name), Prioritized {
    override fun buildRoot(): SimpleNode = Par("strat", true,
            {
                if (status == NodeStatus.INITIAL) {
                    println("Selected strategy: $name with bound $priority")
                    diag.log("Selected strategy: $name with bound $priority")
                    Styx.strategy = name
                }
                NodeStatus.DONE
            },
            *nodes)

    override var priority: Double = 0.0

    override fun loadLearned() {
        super.loadLearned()
        val n = relevantGameResults.amount
        val j = relevantGameResults.filteredByStrategy(name)
        val nj = j.amount
        val xj = j.score
        priority = xj + sqrt(1.5 * ln(n.toDouble() + 1) / (nj + 1)) + random() * 0.0000001
        println("Info for strategy $name: winrate: $xj, tried: $nj/$n; bound: $priority")
        diag.log("Info for strategy $name: winrate: $xj, tried: $nj/$n; bound: $priority")
    }

    override fun reset() {
        error("ERROR!")
    }
}

object TwoHatchMuta : Strat("2HatchMuta",
        twelveHatchBasic,
        Repeat(delegate = Get(12, UnitType.Zerg_Drone)),
        Build(UnitType.Zerg_Extractor),
        Repeat(delegate = Get(8, UnitType.Zerg_Zergling)),
        Morph(UnitType.Zerg_Lair),
        Get(3, UnitType.Zerg_Overlord),
        Upgrade(lingSpeedUpgrade, 1),
        Get(20, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spire),
        Get(22, UnitType.Zerg_Drone),
        Expand(),
        Build(UnitType.Zerg_Extractor),
        Repeat(delegate = Get(22, UnitType.Zerg_Drone)),
        Get(5, UnitType.Zerg_Overlord),
        ensureSupply(),
        Repeat(delegate = Seq("Add Lings",
                WaitFor { resources.availableGMS.minerals > 800 },
                pumpLings()
        )),
        pumpMutas(),
        Expand()
)

object TwoHatchHydra : Strat("2HatchHydra",
        twelveHatchBasic,
        Build(UnitType.Zerg_Extractor),
        Get(12, UnitType.Zerg_Drone),
        Repeat(delegate = Get(6, UnitType.Zerg_Zergling)),
        Build(UnitType.Zerg_Hydralisk_Den),
        Repeat(delegate = Get(13, UnitType.Zerg_Drone)),
        Train(UnitType.Zerg_Overlord),
        Upgrade(hydraRangeUpgrade, 1),
        ensureSupply(),
        Repeat(delegate = Seq("More hatches",
                WaitFor { resources.availableGMS.minerals > 600 },
                Build(UnitType.Zerg_Hatchery))
        ),
        pumpHydras()
)

object ThirteenPoolMuta : Strat("13PoolMuta",
        Get(9, UnitType.Zerg_Drone),
        Get(2, UnitType.Zerg_Overlord),
        Get(13, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Build(UnitType.Zerg_Extractor),
        Get(13, UnitType.Zerg_Drone),
        Expand(),
        Morph(UnitType.Zerg_Lair),
        Get(13, UnitType.Zerg_Drone),
        Repeat(delegate = Get(4, UnitType.Zerg_Zergling)),
        Get(14, UnitType.Zerg_Drone),
        Get(3, UnitType.Zerg_Overlord),
        Repeat(delegate = Get(16, UnitType.Zerg_Drone)),
        Build(UnitType.Zerg_Spire),
        Get(1, UnitType.Zerg_Creep_Colony),
        Morph(UnitType.Zerg_Sunken_Colony),
        Repeat(delegate = Get(18, UnitType.Zerg_Drone)),
        Get(5, UnitType.Zerg_Overlord),
        Build(UnitType.Zerg_Extractor),
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
        Repeat(delegate = Get(6, UnitType.Zerg_Zergling)),
        Repeat(delegate = Get(22, UnitType.Zerg_Drone)),
        ensureSupply(),
        Get(5, UnitType.Zerg_Hydralisk),
        Upgrade(hydraRangeUpgrade, 1),
        Expand(),
        Get(15, UnitType.Zerg_Hydralisk),
        Build(UnitType.Zerg_Hatchery),
        Morph(UnitType.Zerg_Lair),
        Get(20, UnitType.Zerg_Hydralisk),
        Build(UnitType.Zerg_Evolution_Chamber),
        Get(23, UnitType.Zerg_Hydralisk),
        Build(UnitType.Zerg_Hatchery),
        Upgrade(UpgradeType.Zerg_Missile_Attacks, 1),
        Upgrade(UpgradeType.Metabolic_Boost, 1),
        Repeat(delegate = Get({ units.my(UnitType.Zerg_Hydralisk).size * 7 / 10 }, UnitType.Zerg_Zergling, true)),
        pumpHydras()
)

object NinePoolCheese : Strat("9 Pool Cheese",
        Get(9, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Train(UnitType.Zerg_Overlord),
        ensureSupply(),
        repeat("Initial lings", 3) { Train(UnitType.Zerg_Zergling) },
        haveBasicZerglingSquad(),
        Repeat(delegate = Get(7, UnitType.Zerg_Drone)),
        pumpLings(),
        Get(2, UnitType.Zerg_Hatchery),
        Build(UnitType.Zerg_Extractor),
        Upgrade(lingSpeedUpgrade, 1),
        Gathering(true)
)

val ninePoolBasic = Par("9 Pool", true,
        Get(9, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool)
)

val overPoolBasic = Par("Overpool", true,
        Get(9, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Build(UnitType.Zerg_Spawning_Pool),
        Get(11, UnitType.Zerg_Drone),
        Expand()
)

val twelveHatchBasic = Par("12 Hatch", true,
        Get(9, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Get(12, UnitType.Zerg_Drone),
        Expand(),
        Build(UnitType.Zerg_Spawning_Pool)
)


private fun pumpLings() = Repeat(delegate = Get(200, UnitType.Zerg_Zergling, true))
private fun pumpHydras() = Repeat(delegate = Get(200, UnitType.Zerg_Hydralisk, true))
private fun pumpMutas() = Repeat(delegate = Get(200, UnitType.Zerg_Mutalisk, true))
private fun haveBasicZerglingSquad() = Repeat(delegate = Get(6, UnitType.Zerg_Zergling))
private fun ensureSupply() = Repeat(delegate = Seq("Supply", WaitFor {
    economy.supplyWithPlanned < 4
}, Train(UnitType.Zerg_Overlord)))