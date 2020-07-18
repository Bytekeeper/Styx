package org.styx.task

import bwapi.UnitType
import bwapi.UpgradeType
import org.bk.ass.bt.*
import org.bk.ass.manage.GMS
import org.bk.ass.sim.AgentUtil
import org.bk.ass.sim.JBWAPIAgentFactory
import org.styx.*
import org.styx.Styx.balance
import org.styx.Styx.bases
import org.styx.Styx.diag
import org.styx.Styx.economy
import org.styx.Styx.fastSim
import org.styx.Styx.relevantGameResults
import org.styx.Styx.units
import org.styx.macro.*
import java.lang.Math.random
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sqrt

open class Strat(
        name: String,
        private vararg val nodes: TreeNode) : BehaviorTree() {
    private var utility = 0.0
    override fun getUtility(): Double = utility

    init {
        withName(name)
    }

    override fun getRoot(): TreeNode = Parallel(
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
        utility = xj + sqrt(1.0 * ln(n.toDouble() + 1) / (nj + 1)) + random() * 0.01
        println("Info for strategy $name: winrate: $xj, tried: $nj/$n; bound: $utility")
        diag.log("Info for strategy $name: winrate: $xj, tried: $nj/$n; bound: $utility")
    }

    override fun reset() {
        error("ERROR!")
    }
}

object TenHatch : Strat("10Hatch",
        Reactions,
        Get(9, UnitType.Zerg_Drone),
        ExtractorTrick(),
        Expand(),
        Get(1, UnitType.Zerg_Spawning_Pool),
        Get(9, UnitType.Zerg_Drone),
        Get(2, UnitType.Zerg_Overlord),
        ExtractorTrick(),
        Build(UnitType.Zerg_Hatchery),
        Repeat(Get(8, UnitType.Zerg_Drone)),
        Get(8, UnitType.Zerg_Zergling),
        ensureSupply(),
        FreeForm
)

object TwoHatchMuta : Strat("2HatchMuta",
        twelveHatchBasic,
        Get(12, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Extractor),
        Repeat(Get(8, UnitType.Zerg_Zergling)),
        Repeat(Get(12, UnitType.Zerg_Drone)),
        Morph(UnitType.Zerg_Lair),
        Get(3, UnitType.Zerg_Overlord),
        upgradeLingSpeed,
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
                        Condition { ResourceReservation.gms.gas < UnitType.Zerg_Mutalisk.gasPrice() },
                        pumpLings()
                )
        ),
        FreeForm
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
        upgradeLingSpeed,
        Get(23, UnitType.Zerg_Drone),
        Repeat(Get(1, UnitType.Zerg_Spire)),
        Get(23, UnitType.Zerg_Drone),
        Get(4, UnitType.Zerg_Overlord),
        Repeat(Get(25, UnitType.Zerg_Drone)),
        Get(12, UnitType.Zerg_Zergling),
        Expand(),
        Get(6, UnitType.Zerg_Overlord),
        ensureSupply(),
        FreeForm
)

object TwoHatchHydra : Strat("2HatchHydra",
        twelveHatchBasic,
        Build(UnitType.Zerg_Extractor),
        Get(13, UnitType.Zerg_Drone),
        Get(1, UnitType.Zerg_Hydralisk_Den),
        Repeat(Get(16, UnitType.Zerg_Drone)),
        Train(UnitType.Zerg_Overlord),
        Upgrade(hydraRangeUpgrade, 1),
        Get(12, UnitType.Zerg_Hydralisk),
        Upgrade(hydraSpeedUpgrade, 1),
        ensureSupply(),
        Repeat(Repeat.Policy.SELECTOR,
                Sequence(
                        Condition { ResourceReservation.gms.minerals > 600 },
                        Build(UnitType.Zerg_Hatchery)
                )
        ),
        Repeat(Repeat.Policy.SELECTOR,
                Sequence(
                        Condition { ResourceReservation.gms.gas < UnitType.Zerg_Hydralisk.gasPrice() },
                        pumpLings()
                )
        ),
        Train(UnitType.Zerg_Lurker),
        FreeForm
)

object ThirteenPoolMuta : Strat("13PoolMuta",
        Reactions,
        Get(9, UnitType.Zerg_Drone),
        Get(2, UnitType.Zerg_Overlord),
        Get(12, UnitType.Zerg_Drone),
        Get(1, UnitType.Zerg_Spawning_Pool),
        Get(12, UnitType.Zerg_Drone),
        Build(UnitType.Zerg_Extractor),
        Get(12, UnitType.Zerg_Drone),
        Expand(),
        Morph(UnitType.Zerg_Lair),
        Repeat(Get(4, UnitType.Zerg_Zergling)),
        Get(14, UnitType.Zerg_Drone),
        Get(3, UnitType.Zerg_Overlord),
        Repeat(Get(15, UnitType.Zerg_Drone)),
        Get(1, UnitType.Zerg_Spire),
        Get(1, UnitType.Zerg_Creep_Colony),
        Morph(UnitType.Zerg_Sunken_Colony),
        Build(UnitType.Zerg_Extractor),
        Repeat(Get(18, UnitType.Zerg_Drone)),
        Get(5, UnitType.Zerg_Overlord),
        ensureSupply(),
        FreeForm
)

object Nine734 : Strat("9734",
        overPoolBasic,
        Get(2, UnitType.Zerg_Zergling),
        Get(12, UnitType.Zerg_Drone),
        Expand(),
        Build(UnitType.Zerg_Extractor),
        Get(17, UnitType.Zerg_Drone),
        Get(1, UnitType.Zerg_Hydralisk_Den),
        Get(3, UnitType.Zerg_Overlord),
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
        Get(1, UnitType.Zerg_Evolution_Chamber),
        Get(23, UnitType.Zerg_Hydralisk),
        Build(UnitType.Zerg_Hatchery),
        Upgrade(UpgradeType.Zerg_Missile_Attacks, 1),
        Upgrade(UpgradeType.Metabolic_Boost, 1),
        FreeForm
)

object NinePoolCheese : Strat("9 Pool Cheese",
        ninePoolBasic,
        Train(UnitType.Zerg_Overlord),
        ensureSupply(),
        haveBasicZerglingSquad(),
        Repeat(Get(7, UnitType.Zerg_Drone)),
        pumpLings(),
        Get(2, UnitType.Zerg_Hatchery),
        Build(UnitType.Zerg_Extractor),
        upgradeLingSpeed
)

object TwelveHatchCheese : Strat("12 Hatch Cheese",
        Reactions,
        Get(9, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Get(12, UnitType.Zerg_Drone),
        Get(2, UnitType.Zerg_Hatchery),
        Get(1, UnitType.Zerg_Spawning_Pool),
        Get(13, UnitType.Zerg_Drone),
//        Get(3, UnitType.Zerg_Hatchery),
        Get(1, UnitType.Zerg_Extractor),
        haveBasicZerglingSquad(),
        Train(UnitType.Zerg_Overlord),
        ensureSupply(),
        Train(UnitType.Zerg_Zergling),
        Repeat(Get(12, UnitType.Zerg_Drone)),
        upgradeLingSpeed,
        FreeForm
)

object FourPool : Strat("4pool",
        Get(1, UnitType.Zerg_Spawning_Pool),
        Get(12, UnitType.Zerg_Zergling),
        ExtractorTrick(UnitType.Zerg_Zergling),
        Repeat(Get(3, UnitType.Zerg_Drone)),
        ensureSupply(),
        pumpLings()
)

val ninePoolBasic = Parallel(
        Reactions,
        Get(9, UnitType.Zerg_Drone),
        Get(1, UnitType.Zerg_Spawning_Pool)
).withName("ninePoolBasic")

val overPoolBasic = Parallel(
        Reactions,
        Get(9, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Get(1, UnitType.Zerg_Spawning_Pool),
        Get(11, UnitType.Zerg_Drone),
        Expand()
).withName("overPoolBasic")

val twelveHatchBasic = Parallel(
        Reactions,
        Get(9, UnitType.Zerg_Drone),
        Train(UnitType.Zerg_Overlord),
        Get(12, UnitType.Zerg_Drone),
        Expand(),
        Get(1, UnitType.Zerg_Spawning_Pool)
).withName("twelveHatchBasic")


val upgradeLingSpeed = Upgrade(lingSpeedUpgrade, 1)
private fun pumpLings() = Repeat(Get(200, UnitType.Zerg_Zergling, true))
private fun pumpHydras() = Repeat(Get(200, UnitType.Zerg_Hydralisk, true))
fun pumpMutas() = Repeat(Get(200, UnitType.Zerg_Mutalisk, true))
private fun haveBasicZerglingSquad() = Repeat(Get(6, UnitType.Zerg_Zergling))
fun ensureSupply() =
        Repeat(Repeat.Policy.SELECTOR,
                Sequence(
                        Condition {
                            economy.supplyWithPlanned < 4 ||
                                    economy.supplyWithPlanned < 16 && ResourceReservation.gms.minerals > 400
                        },
                        Train(UnitType.Zerg_Overlord)
                )
        )

private val agentFactory = JBWAPIAgentFactory()

object FreeForm : Best(
//        // Gatekeeper
//        object : LambdaNode({ NodeStatus.RUNNING }) {
//            override fun getUtility(): Double = 0.5
//        },
        object : Repeat(Train(UnitType.Zerg_Drone)) {
            override fun getUtility(): Double =
                    fastSig(balance.evalMyCombatVsMobileGroundEnemy.value / (balance.evalMyMobileVsAllEnemy.value + 1.000) * 3.2 - units.my(UnitType.Zerg_Drone).size * 0.005)
        },
        object : Repeat(Build(UnitType.Zerg_Hatchery)) {
            override fun getUtility(): Double =
                    fastSig((ResourceReservation.gms + economy.estimatedAdditionalGMSIn(200)).minerals / (units.my(UnitType.Zerg_Hatchery).size + 1.0 + units.myProjectedNew(UnitType.Zerg_Hatchery)) * 0.003)
        },
        object : Repeat(
                Selector(
                        Condition { bases.myBases.sumBy { it.geysers.size } == 0 },
                        Condition { economy.currentResources.minerals < 400 || economy.currentResources.gas > 800 },
                        Build(UnitType.Zerg_Extractor)
                )
        ) {
            override fun getUtility(): Double = min(economy.currentResources.minerals / 800.0, 1 - economy.currentResources.gas / 1600.0)
        },
        object : Repeat(Expand()) {
            override fun getUtility(): Double =
                    units.myWorkers.size / (bases.myBases.size + 0.1) / 24.0
        },
        // TODO: Replace with "generated" list
        UTrain(UnitType.Zerg_Guardian),
        UTrain(UnitType.Zerg_Hydralisk),
        UTrain(UnitType.Zerg_Zergling),
        UTrain(UnitType.Zerg_Ultralisk),
        UTrain(UnitType.Zerg_Mutalisk),
//        UTrain(UnitType.Zerg_Scourge),
        UUpgrade(UpgradeType.Zerg_Missile_Attacks, 1),
        UUpgrade(lingSpeedUpgrade, 1),
        UUpgrade(hydraRangeUpgrade, 1),
        UUpgrade(hydraSpeedUpgrade, 1)
) {
    override fun getUtility(): Double = 0.0
}

val fastSimmed by LazyOnFrame {
    fastSim.reset()
    units.mine.forEach { fastSim.addAgentA(it.agent()) }
    units.enemy.forEach { fastSim.addAgentB(it.agent()) }
    fastSim.simulate(500)
    fastSim.evalToInt(agentValueForPlayer)
}

class UUpgrade(private val upgradeType: UpgradeType, level: Int) : Memo(object : Upgrade(upgradeType, level) {
    override fun getUtility(): Double = units.mine.count { it.unitType in UpgradeType.Zerg_Missile_Attacks.whatUses() } / 15.0
})

class UTrain(private val unitType: UnitType) : Repeat(StartTrain(unitType)) {
    private val perWorkerPerFrameMinerals = 0.046
    private val perWorkerPerFrameGas = 0.069
    private val deltaFrames = 1200

    override fun getUtility(): Double {
        val price = missingUnitsToBuildIncluding(unitType)
                .fold(GMS.ZERO) { gms, ut ->
                    GMS.unitCost(ut) + gms
                }

        val gasWorkers = min(units.myWorkers.size / 12 * 4, bases.myBases.count { it.hasGas } * 4)
        val mineralWorkers = units.myWorkers.size - gasWorkers

        val have = GMS((perWorkerPerFrameGas * deltaFrames * gasWorkers).toInt(), (perWorkerPerFrameMinerals * deltaFrames * mineralWorkers).toInt(), 0) +
                economy.currentResources
        val costPerUnit = GMS.unitCost(unitType)
        val haveAfterPre = have - price + costPerUnit
        val potentialCount = haveAfterPre.div(GMS(costPerUnit.gas, costPerUnit.minerals, 0)) * (if (unitType.isTwoUnitsInOneEgg) 2 else 1)
        val items = (fastSig(potentialCount.toDouble() * 0.05) * 30).toInt()

        val myAgents = units.mine.map { it.agent() } + (1..items).map { agentFactory.of(unitType) }
        val enemyAgents = units.enemy.map { it.agent() }
        fastSim.reset()
        myAgents.forEach { fastSim.addAgentA(it) }
        enemyAgents.forEach { fastSim.addAgentB(it) }
        AgentUtil.randomizePositions(myAgents, 100, 100, 200, 300)
        AgentUtil.randomizePositions(enemyAgents, 300, 100, 400, 300)
        fastSim.simulate(800)
        val result = fastSim.evalToInt(agentValueForPlayer)
        if (Styx.units.myCompleted(UnitType.Zerg_Lair).isNotEmpty()) {
//            System.err.println("!")
        }
        val evaluated = fastSig(result.cross(fastSimmed).toDouble() / 200000.0) * 0.8
        return evaluated
    }
}

fun missingUnitsToBuildIncluding(wanted: UnitType): List<UnitType> {
    val visited = mutableSetOf<UnitType>()
    val missing = mutableListOf<UnitType>()
    fun process(current: UnitType) {
        if (current == UnitType.None || current.isWorker || current == UnitType.Zerg_Larva || current in visited) return
        visited += current
        if (wanted == current || units.mine.none { it.isA(current) || it.unitType == current }) missing += current
        process(current.whatBuilds().first);
        for (requiredUnit in current.requiredUnits().keys) {
            process(requiredUnit);
        }
        process(current.requiredTech().requiredUnit())
        process(current.requiredTech().whatResearches())
    }
    process(wanted)
    return missing
}