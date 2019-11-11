package org.styx.task

import org.styx.BTNode
import org.styx.Par
import org.styx.Styx
import org.styx.squad.*

object SquadDispatch : Par("SquadDispatch") {
    private val squads = mutableMapOf<Squad, BTNode>()

    override val children: Collection<BTNode>
        get() {
            squads.keys.retainAll(Styx.squads.squads)
            Styx.squads.squads.forEach { squad ->
                squads.computeIfAbsent(squad) { squadBoard ->
                    Par("Squad",
                            LocalCombat(squadBoard),
                            SeekCombatSquad(squadBoard),
                            SquadBackOff(squadBoard),
                            SquadScout(squadBoard))
                }
            }
            return squads.values
        }
}

