package org.styx.task

import org.bk.ass.BWMirrorAgentFactory
import org.bk.ass.Evaluator
import org.bk.ass.cluster.Cluster
import org.styx.*
import org.styx.squad.SquadAttack
import org.styx.squad.SquadBackOff
import org.styx.squad.SquadBoard
import org.styx.squad.SquadFightLocal

object SquadDispatch : BTNode() {
    private val evaluator = Evaluator()
    private val agentFactory = BWMirrorAgentFactory()
    private val clusters = mutableMapOf<Cluster<SUnit>, BehaviorTree>()

    override fun tick(): NodeStatus {
        clusters.keys.retainAll(Styx.clusters.clusters)
        Styx.clusters.clusters.forEach { cluster ->
            val units = cluster.elements.toMutableList()
            with(clusters.computeIfAbsent(cluster) {
                BehaviorTree(Par("Squad",
                        SquadFightLocal(), SquadAttack(), SquadBackOff()
                ), SquadBoard())
            }.board<SquadBoard>()) {
                mine = units.filter { it.myUnit }
                enemies = units.filter { !it.myUnit }
                fastEval = evaluator.evaluate(mine.filter { it.unitType.canAttack() }.map { agentFactory.of(it.unit) },
                        enemies.filter { it.unitType.canAttack() }.map { agentFactory.of(it.unit) })
            }
        }
        clusters.values.forEach { it.tick() }

        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        clusters.values.forEach { it.reset() }
    }
}

