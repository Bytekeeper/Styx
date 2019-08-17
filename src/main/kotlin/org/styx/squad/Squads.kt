package org.styx.squad

import org.bk.ass.BWMirrorAgentFactory
import org.bk.ass.Evaluator
import org.styx.*
import org.styx.Styx.clusters
import org.styx.action.BasicActions

class SquadBoard {
    var fastEval: Double = 0.5
    var mine = emptyList<SUnit>()
    var enemies = emptyList<SUnit>()
}

class SquadFightLocal : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { board<SquadBoard>().mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun tick(): NodeStatus {
        val board = board<SquadBoard>()
        val enemies = board.enemies
        if (enemies.isEmpty()) return NodeStatus.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty()) return NodeStatus.RUNNING
        val attackers = attackerLock.units
        if (board.mine.none { it.unitType.isBuilding } && board.fastEval < 0.6)
            return NodeStatus.RUNNING

        attackers.forEach { attacker ->
            val target = TargetEvaluator.bestTarget(attacker, enemies) ?: return@forEach
            BasicActions.attack(attacker, target)
        }
        return NodeStatus.RUNNING
    }
}

class SquadAttack : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { board<SquadBoard>().mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    private val bwMirrorAgentFactory = BWMirrorAgentFactory()
    private val evaluator = Evaluator()

    override fun tick(): NodeStatus {
        val enemyClusters = Styx.clusters.clusters.filter { it.elements.any { it.enemyUnit } }
        val targetCluster = enemyClusters.maxBy { it.elements.count { it.myUnit } - it.elements.count { it.enemyUnit } }
                ?: return NodeStatus.RUNNING
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty()) return NodeStatus.RUNNING
        val attackers = attackerLock.units

        val myAgents = (attackers + targetCluster.elements.filter { it.myUnit }).distinct().map { bwMirrorAgentFactory.of(it.unit) }
        val enemies = (board<SquadBoard>().enemies + targetCluster.elements.filter { it.enemyUnit }).distinct().map { bwMirrorAgentFactory.of(it.unit) }
        val evaluate = evaluator.evaluate(myAgents, enemies)
        if (evaluate > 0.7) {
            attackers.forEach { attacker ->
                val target = TargetEvaluator.bestTarget(attacker, Styx.units.enemy) ?: return@forEach
                BasicActions.attack(attacker, target)
            }
        } else {
            attackerLock.release()
        }
        return NodeStatus.RUNNING
    }
}

class SquadBackOff : BTNode() {
    private val attackerLock = UnitLocks { Styx.resources.availableUnits.filter { board<SquadBoard>().mine.contains(it) && !it.unitType.isWorker && !it.unitType.isBuilding && it.unitType.canAttack() } }

    override fun tick(): NodeStatus {
        attackerLock.reacquire()
        if (attackerLock.units.isEmpty()) return NodeStatus.RUNNING
        val targetCluster = clusters.clusters.minBy { it.elements.count { it.enemyUnit } } ?: return NodeStatus.RUNNING
        val targetUnit = targetCluster.elements.first { it.myUnit }
        attackerLock.units.forEach {
            if (it.distanceTo(targetUnit) > 100) BasicActions.move(it, targetUnit.position)
        }
        return NodeStatus.RUNNING
    }

}

