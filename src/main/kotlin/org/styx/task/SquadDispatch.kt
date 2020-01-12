package org.styx.task

import org.bk.ass.bt.BehaviorTree
import org.bk.ass.bt.Distributor
import org.bk.ass.bt.Parallel
import org.styx.Styx
import org.styx.squad.*

object SquadDispatch : BehaviorTree() {
    override fun getRoot() = Distributor(
            Parallel.Policy.SEQUENCE,
            { Styx.squads.squads }
    ) { squadBoard ->
        Parallel(Parallel.Policy.SEQUENCE,
                LocalCombat(squadBoard),
                SquadBackOff(squadBoard),
                SeekCombatSquad(squadBoard),
                SquadScout(squadBoard),
                ClusterTogether(squadBoard))
    }
}