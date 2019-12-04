package org.styx.task

import bwapi.UnitType
import bwapi.UpgradeType
import org.styx.*
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
        vararg nodes: BTNode) : Par(name, *nodes) {
    override var priority: Double = 0.0

    override fun loadLearned() {
        super.loadLearned()
        val n = relevantGameResults.size
        val j = relevantGameResults.filter { it.strategy == name }
        val nj = j.size
        val xj = j.map { if (it.won) 1.0 else 0.0 }.average().orZero()
        priority = xj + sqrt(2 * ln(n.toDouble() + 1) / (nj + 1)) + random() * 0.0000001
    }

    override fun tick(): NodeStatus {
        if (status == NodeStatus.INITIAL) {
            println("Selected strategy: $name with bound $priority")
            Styx.strategy = name
        }
        return super.tick()
    }
}

object TwoHatchMuta : Strat("2HatchMuta",
        TwelveHatchBasic,
        Build(UnitType.Zerg_Extractor),
        Get({ 14 }, UnitType.Zerg_Drone),
        Morph(UnitType.Zerg_Lair),
        Build(UnitType.Zerg_Extractor),
        Repeat(delegate = Get({ 21 }, UnitType.Zerg_Drone)),
        Train(UnitType.Zerg_Overlord),
        Expand(),
        Build(UnitType.Zerg_Spire),
        Train(UnitType.Zerg_Overlord),
        Train(UnitType.Zerg_Overlord),
        Repeat(delegate = Get({ 24 }, UnitType.Zerg_Drone)),
        ensureSupply(),
        pumpMutas()
)

object TwoHatchHydra : Strat("2HatchHydra",
        TwelveHatchBasic,
        Build(UnitType.Zerg_Extractor),
        Get({ 14 }, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Repeat(delegate = Get({ 2 }, UnitType.Zerg_Zergling)),
        Build(UnitType.Zerg_Hydralisk_Den),
        Repeat(delegate = Get({ 15 }, UnitType.Zerg_Drone)),
        Upgrade(hydraSpeedUpgrade, 1),
//        Train(UnitType.Zerg_Overlord),
        ensureSupply(),
        Seq("Hydra Range",
                Condition { units.my(UnitType.Zerg_Hydralisk).size > 10 },
                Upgrade(hydraRangeUpgrade, 1)
        ),
        Repeat(delegate = Seq("More hatches",
                Condition { resources.availableGMS.minerals > 600 },
                Build(UnitType.Zerg_Hatchery))
        ),
        pumpHydras()
)

object Nine734 : Strat("9734",
        OverPoolBasic,
        Get({ 12 }, UnitType.Zerg_Drone),
        Expand(),
        Build(UnitType.Zerg_Extractor),
        Get({ 17 }, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Hydralisk_Den),
        Train(UnitType.Zerg_Overlord),
        Get({ 18 }, UnitType.Zerg_Drone),
        Upgrade(hydraSpeedUpgrade, 1),
        Repeat(delegate = Get({ 6 }, UnitType.Zerg_Zergling)),
        Repeat(delegate = Get({ 22 }, UnitType.Zerg_Drone)),
        ensureSupply(),
        Get({ 5 }, UnitType.Zerg_Hydralisk),
        Upgrade(UpgradeType.Grooved_Spines, 1),
        Expand(),
        Get({ 15 }, UnitType.Zerg_Hydralisk),
        Build(UnitType.Zerg_Hatchery),
        Morph(UnitType.Zerg_Lair),
        Get({ 20 }, UnitType.Zerg_Hydralisk),
        Build(UnitType.Zerg_Evolution_Chamber),
        Get({ 23 }, UnitType.Zerg_Hydralisk),
        Build(UnitType.Zerg_Hatchery),
        Upgrade(UpgradeType.Zerg_Missile_Attacks, 1),
        Upgrade(UpgradeType.Metabolic_Boost, 1),
        Repeat(delegate = Get({ units.my(UnitType.Zerg_Hydralisk).size * 7 / 10 }, UnitType.Zerg_Zergling)),
        pumpHydras()
)

object TwelveHatch : Strat("12HatchHydra",
        Get({ 9 }, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Train(UnitType.Zerg_Drone),
        ExtractorTrick(),
        Train(UnitType.Zerg_Overlord),
        Repeat(delegate = Seq("Supply", Condition {
            economy.supplyWithPlanned < 3
        }, Train(UnitType.Zerg_Overlord))),
        repeat("Initial lings", 3) { Train(UnitType.Zerg_Zergling) },
        Expand(),
        haveBasicZerglingSquad(),
        Build(UnitType.Zerg_Extractor),
        pumpHydras()
//        Repeat(delegate = Seq("Train workers", Condition {
//            min(FollowBO.workerAmountBaseOnSupply(), FollowBO.workerAmountRequiredToFullyUtilize()) > units.workers.size + buildPlan.plannedUnits.count { it.isWorker }
//        }, Train(UnitType.Zerg_Drone))),
)

object NinePoolCheese : Strat("9 Pool Cheese",
        Get({ 9 }, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Train(UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        ensureSupply(),
        repeat("Initial lings", 3) { Train(UnitType.Zerg_Zergling) },
        haveBasicZerglingSquad(),
        Repeat(delegate = Get({ 7 }, UnitType.Zerg_Drone)),
        pumpLings(),
//        Repeat(delegate = Seq("Train workers", Condition {
//            min(FollowBO.workerAmountBaseOnSupply(), FollowBO.workerAmountRequiredToFullyUtilize()) > units.workers.size + buildPlan.plannedUnits.count { it.isWorker }
//        }, Train(UnitType.Zerg_Drone))),
        Get({ 2 }, UnitType.Zerg_Hatchery),
        Build(UnitType.Zerg_Extractor),
        Upgrade(lingSpeedUpgrade, 1),
        Gathering(true)
)

object NinePoolBasic : Par("9 Pool",
        Get({ 9 }, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Spawning_Pool),
        Get({ 11 }, UnitType.Zerg_Drone)
)

object OverPoolBasic : Par("Overpool",
        Get({ 9 }, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Build(UnitType.Zerg_Spawning_Pool),
        Get({ 11 }, UnitType.Zerg_Drone),
        Expand(),
        Get({ 2 }, UnitType.Zerg_Zergling)
)

object TwelveHatchBasic : Par("12 Hatch",
        Get({ 9 }, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Get({ 12 }, UnitType.Zerg_Drone),
        Expand(),
        Build(UnitType.Zerg_Spawning_Pool)
)


private fun pumpLings() = Repeat(delegate = Get({ 200 }, UnitType.Zerg_Zergling))
private fun pumpHydras() = Repeat(delegate = Get({ 200 }, UnitType.Zerg_Hydralisk))
private fun pumpMutas() = Repeat(delegate = Get({ 200 }, UnitType.Zerg_Mutalisk))
private fun haveBasicZerglingSquad() = Repeat(delegate = Get({ 6 }, UnitType.Zerg_Zergling))
private fun ensureSupply() = Repeat(delegate = Seq("Supply", Condition {
    economy.supplyWithPlanned < 4
}, Train(UnitType.Zerg_Overlord)))