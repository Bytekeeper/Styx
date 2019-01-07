package org.styx

import org.styx.Context.find
import org.styx.Context.frameCount
import org.styx.Context.self
import kotlin.math.max

class Economy {
    private var lastGas = 0
    private var lastMinerals = 0
    private var lastFrame = 0
    var gasPerFramePerWorker = 0.0
    var mineralsPerFramePerWorker = 0.041

    fun update() {
        val frameDelta = frameCount - lastFrame
        if (frameDelta > 0 && frameCount % 24 == 0) {
            val mineralDelta = max(0, self.gatheredMinerals() - lastMinerals).toDouble() / frameDelta / (find.myWorkers.count { it.isGatheringMinerals } + 0.01)
            val gasDelta = max(0, self.gatheredGas() - lastGas).toDouble() / frameDelta / (find.myWorkers.count { it.isGatheringGas } + 0.01)
            mineralsPerFramePerWorker = 0.990 * mineralsPerFramePerWorker + 0.010 * mineralDelta
            gasPerFramePerWorker = 0.990 * gasPerFramePerWorker + 0.010 * gasDelta
            lastFrame = frameCount
            lastGas = self.gatheredGas()
            lastMinerals = self.gatheredMinerals()
        }
    }

    fun reset() {
        lastGas = self.gas()
        lastMinerals = self.minerals()
        lastFrame = frameCount
    }
}