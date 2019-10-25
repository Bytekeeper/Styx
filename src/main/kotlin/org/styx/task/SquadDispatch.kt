package org.styx.task

import org.styx.BTNode
import org.styx.NodeStatus
import org.styx.Par
import org.styx.Styx
import org.styx.squad.SquadAttack
import org.styx.squad.SquadBackOff
import org.styx.squad.SquadBoard
import org.styx.squad.SquadFightLocal

object SquadDispatch : BTNode() {
    private val squads = mutableMapOf<SquadBoard, BTNode>()

    override fun tick(): NodeStatus {
        squads.keys.retainAll(Styx.squads.squads)
        Styx.squads.squads.forEach { squad ->
            squads.computeIfAbsent(squad) { squadBoard ->
                Par("Squad", SquadFightLocal(squadBoard), SquadAttack(squadBoard), SquadBackOff(squadBoard))
            }
        }
        squads.values.forEach { it.tick() }

        return NodeStatus.RUNNING
    }

    override fun reset() {
        super.reset()
        squads.values.forEach { it.reset() }
    }
}

