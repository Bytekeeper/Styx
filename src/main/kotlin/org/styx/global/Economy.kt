package org.styx.global

import bwapi.UnitType
import org.bk.ass.manage.GMS
import org.styx.LazyOnFrame
import org.styx.Styx
import org.styx.plus

class Economy {
    // "Stolen" from PurpleWave
    private val perWorkerPerFrameMinerals = 0.046
    private val perWorkerPerFrameGas = 0.069
    private var workersOnMinerals: Int = 0
    private var workersOnGas: Int = 0
    lateinit var currentResources: GMS
        private set

    private val supplyWithPending: Int by LazyOnFrame {
        Styx.self.supplyTotal() - Styx.self.supplyUsed() +
                Styx.units.myPending.sumBy { it.unitType.supplyProvided() - it.unitType.supplyRequired() }
    }

    val supplyWithPlanned: Int
        get() =
            supplyWithPending + Styx.buildPlan.plannedUnits.sumBy {
                val u = it.type
                u.supplyProvided() +
                        -u.supplyRequired() +
                        (if (u.whatBuilds().first == UnitType.Zerg_Drone) 1 else 0)
            }

    // TODO: Missing additional supply in the given time
    fun estimatedAdditionalGMSIn(frames: Int): GMS {
        require(frames >= 0)
        val lostWorkers = Styx.buildPlan.plannedUnits
                .filter { it.consumedUnit?.isWorker == true }
                .mapNotNull { if (it.framesToStart != null && it.framesToStart <= frames) it.framesToStart else null }
                .sortedBy { it }
        var frame = 0
        var gasWorkers = workersOnGas
        var mineralWorkers = workersOnMinerals
        val estimatedAfterWorkerloss = lostWorkers.fold(GMS(0, 0, 0)) { acc, f ->
            val deltaFrames = f - frame
            if (mineralWorkers > 0)
                mineralWorkers--
            else if (gasWorkers > 0)
                gasWorkers--
            frame = f
            acc + GMS((perWorkerPerFrameGas * deltaFrames * gasWorkers).toInt(), (perWorkerPerFrameMinerals * deltaFrames * mineralWorkers).toInt(), 0)
        }
        val deltaFrames = frames - frame
        return estimatedAfterWorkerloss +
                GMS((perWorkerPerFrameGas * deltaFrames * gasWorkers).toInt(), (perWorkerPerFrameMinerals * deltaFrames * mineralWorkers).toInt(), 0)
    }

    fun update() {
        workersOnMinerals = Styx.units.myWorkers.count { it.gatheringMinerals }
        workersOnGas = Styx.units.myWorkers.count { it.gatheringGas }
        currentResources = GMS(Styx.self.gas(), Styx.self.minerals(), Styx.self.supplyTotal() - Styx.self.supplyUsed())
    }
}