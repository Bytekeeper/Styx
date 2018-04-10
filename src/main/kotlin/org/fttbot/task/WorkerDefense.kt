package org.fttbot.task

import org.fttbot.BaseNode
import org.fttbot.Board
import org.fttbot.NodeStatus
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.info.Cluster
import org.fttbot.info.findWorker
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.PlayerUnit
import org.openbw.bwapi4j.unit.Worker

data class WorkerDefenseBoard(val defendingWorkers: MutableList<Worker> = mutableListOf(),
                              var enemies: MutableList<PlayerUnit> = mutableListOf())

class WorkerDefense(val base: Position) : BaseNode<WorkerDefenseBoard>() {
    override fun tick(): NodeStatus {
        val defendingWorkers = board!!.defendingWorkers

        val enemyCluster = Cluster.enemyClusters.minBy { it.position.getDistance(base) } ?: return NodeStatus.SUCCEEDED
        if (enemyCluster.position.getDistance(base) > 300) {
            defendingWorkers.clear()
            board!!.enemies.clear()
            return NodeStatus.SUCCEEDED
        }
        val myCluster = Cluster.myClusters.minBy { it.position.getDistance(base) } ?: return NodeStatus.SUCCEEDED
        val simUnitsOfEnemy = enemyCluster.units.map { SimUnit.of(it) }
        val successForCompleteDefense = CombatEval.probabilityToWin(myCluster.units.filter { it is Attacker }.map { SimUnit.of(it) }, simUnitsOfEnemy)
        if (successForCompleteDefense < 0.4) {
            // TODO: FLEE - you fools!
            return NodeStatus.FAILED
        }
        defendingWorkers.retainAll(Board.resources.units)
        val successForCurrentRatio = CombatEval.probabilityToWin(myCluster.units.filter {
            it.isCompleted && it is Attacker && (it !is Worker || defendingWorkers.contains(it))
        }.map { SimUnit.of(it) }, simUnitsOfEnemy)
        if (successForCurrentRatio > 0.7) {
            defendingWorkers.minBy { it.hitPoints }?.let {
                defendingWorkers.remove(it)
            }
        } else if (successForCurrentRatio < 0.5) {
            findWorker(base, candidates = myCluster.units.filterIsInstance(Worker::class.java).filter { Board.resources.units.contains(it) })?.let {
                defendingWorkers.add(it)
            }
        }
        if (defendingWorkers.isEmpty()) return NodeStatus.SUCCEEDED
        board!!.enemies = enemyCluster.units.toMutableList()
        Board.resources.reserveUnits(defendingWorkers)
        return NodeStatus.SUCCEEDED
    }

}