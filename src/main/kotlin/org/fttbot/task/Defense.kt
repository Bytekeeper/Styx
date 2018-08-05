package org.fttbot.task

import org.fttbot.Board
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.info.Cluster
import org.fttbot.info.MyInfo
import org.fttbot.info.findWorker
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker

class WorkerDefense(val defensePoint: Position) : Task() {
    val defendingWorkers = mutableListOf<Worker>()

    override val utility: Double
        get() = 1.0

    private val defenderCombatTask = CoordinatedAttack()

    override fun processInternal(): TaskStatus {
        val enemyCluster = Cluster.enemyClusters.minBy { it.position.getDistance(defensePoint) }
                ?: return TaskStatus.DONE
        if (enemyCluster.position.getDistance(defensePoint) > 300) {
            defendingWorkers.clear()
            return TaskStatus.DONE
        }
        val myCluster = Cluster.myClusters.minBy { it.position.getDistance(defensePoint) } ?: return TaskStatus.DONE
        val simUnitsOfEnemy = enemyCluster.units.map { SimUnit.of(it) }
        val successForCompleteDefense = CombatEval.probabilityToWin(myCluster.units.filter { it is Attacker }.map { SimUnit.of(it) }, simUnitsOfEnemy)
        if (successForCompleteDefense < 0.4) {
            return TaskStatus.FAILED
        }
        defendingWorkers.retainAll(Board.resources.units)
        val successForCurrentRatio = CombatEval.probabilityToWin(
                (defendingWorkers + myCluster.units.filter {
                    it.isCompleted && it is Attacker && it !is Worker
                }).map { SimUnit.of(it) }, simUnitsOfEnemy)
        if (successForCurrentRatio > 0.7) {
            defendingWorkers.minBy { it.hitPoints }?.let {
                defendingWorkers.remove(it)
            }
        } else if (successForCurrentRatio < 0.5) {
            findWorker(defensePoint, candidates = myCluster.units.filterIsInstance(Worker::class.java).filter { Board.resources.units.contains(it) })?.let {
                defendingWorkers.add(it)
            }
        }
        if (defendingWorkers.isEmpty()) return TaskStatus.DONE
        Board.resources.reserveUnits(defendingWorkers)

        defenderCombatTask.attackers = defendingWorkers
        defenderCombatTask.targets = enemyCluster.units
        defenderCombatTask.process()

        return TaskStatus.RUNNING
    }

    companion object : TaskProvider {
        private val bases = ManagedTaskProvider({ MyInfo.myBases }, { WorkerDefense((it as PlayerUnit).position) })
        override fun invoke(): List<Task> = bases()
    }
}