package org.fttbot.task

import org.fttbot.BaseNode
import org.fttbot.Board
import org.fttbot.NodeStatus
import org.fttbot.estimation.CombatEval
import org.fttbot.estimation.SimUnit
import org.fttbot.info.Cluster
import org.fttbot.info.findWorker
import org.omg.PortableInterceptor.SUCCESSFUL
import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.Attacker
import org.openbw.bwapi4j.unit.Worker

class WorkerDefense(val base: Position) : BaseNode<Any>() {
    val defendingWorkers = mutableListOf<Worker>()

    override fun tick(): NodeStatus {
        val enemyCluster = Cluster.enemyClusters.minBy { it.position.getDistance(base) } ?: return NodeStatus.SUCCEEDED
        if (enemyCluster.position.getDistance(base) > 300) return NodeStatus.SUCCEEDED
        val myCluster = Cluster.myClusters.minBy { it.position.getDistance(base) } ?: return NodeStatus.SUCCEEDED
        val simUnitsOfEnemy = enemyCluster.units.map { SimUnit.of(it) }
        val successForCompleteDefense = CombatEval.probabilityToWin(myCluster.units.filter { it is Attacker }.map { SimUnit.of(it) }, simUnitsOfEnemy)
        if (successForCompleteDefense < 0.5) {
            // TODO: FLEE - you fools!
            return NodeStatus.RUNNING
        }
        defendingWorkers.retainAll(Board.resources.units)
        val successForCurrentRatio = CombatEval.probabilityToWin(myCluster.units.filter {
            it is Attacker && (it !is Worker || defendingWorkers.contains(it))
        }.map { SimUnit.of(it) }, simUnitsOfEnemy)
        if (successForCurrentRatio > 0.7) {
            defendingWorkers.minBy { it.hitPoints }?.let {
                defendingWorkers.remove(it)
            }
        } else if (successForCurrentRatio < 0.5) {
            findWorker(base, candidates = myCluster.units.filterIsInstance(Worker::class.java))?.let {
                defendingWorkers.add(it)
            }
        }
        Board.resources.reserveUnits(defendingWorkers)
        return NodeStatus.SUCCEEDED
    }

}