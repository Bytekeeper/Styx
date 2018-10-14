package org.fttbot.task

import org.bk.ass.BWAPI4JAgentFactory
import org.fttbot.ResourcesBoard
import org.fttbot.estimation.CombatEval
import org.fttbot.info.*
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker

class WorkerDefense(val defensePoint: Position) : Task() {
    val defendingWorkers = mutableListOf<Worker>()

    override val utility: Double
        get() = 1.0

    private val defenderTask = ManagedTaskProvider({ defendingWorkers }, {
        ManageAttacker(it)
    })

    override fun processInternal(): TaskStatus {
        val defenseCluster = Cluster.clusters.minBy { it.position.getDistance(defensePoint) }
                ?: return TaskStatus.DONE
        if (defenseCluster.position.getDistance(defensePoint) > 300) {
            defendingWorkers.clear()
            return TaskStatus.DONE
        }
        val simUnitsOfEnemy = defenseCluster.units
                .filter { it.isEnemyUnit }
                .map { factory.of(it) }
        val successForCompleteDefense = CombatEval.probabilityToWin(defenseCluster.units
                .filter { it.isMyUnit && it is Attacker }
                .map { factory.of(it) }, simUnitsOfEnemy)
        if (successForCompleteDefense < 0.4) {
            return TaskStatus.FAILED
        }
        defendingWorkers.retainAll(ResourcesBoard.units)
        val successForCurrentRatio = CombatEval.probabilityToWin(
                (defendingWorkers + defenseCluster.units.filter {
                    it.isCompleted && it is Attacker && it !is Worker && it.isMyUnit
                }).map { factory.of(it) }, simUnitsOfEnemy)
        if (successForCurrentRatio > 0.7) {
            defendingWorkers.minBy { it.hitPoints }?.let {
                defendingWorkers.remove(it)
            }
        } else if (successForCurrentRatio < 0.5) {
            findWorker(defensePoint, candidates = defenseCluster.units
                    .filterIsInstance<Worker>()
                    .filter { ResourcesBoard.units.contains(it) })?.let {
                defendingWorkers.add(it)
            }
        }
        if (defendingWorkers.isEmpty()) return TaskStatus.DONE
        ResourcesBoard.reserveUnits(defendingWorkers)

        return processAll(defenderTask)
    }

    companion object : TaskProvider {
        private val factory = BWAPI4JAgentFactory()
        private val bases = ManagedTaskProvider({ MyInfo.myBases }, { WorkerDefense((it as PlayerUnit).position).nvr() })
        override fun invoke(): List<Task> = bases()
    }
}