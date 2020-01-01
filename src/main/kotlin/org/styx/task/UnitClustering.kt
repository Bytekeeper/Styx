package org.styx.task

import org.bk.ass.bt.BehaviorTree
import org.bk.ass.bt.Parallel
import org.bk.ass.bt.TreeNode
import org.styx.Dispatch
import org.styx.Styx
import org.styx.squad.*

val squadDispatch = Dispatch(
        { Styx.squads.squads }
) { squadBoard ->
    Parallel(Parallel.Policy.SEQUENCE,
            LocalCombat(squadBoard),
            SeekCombatSquad(squadBoard),
            SquadBackOff(squadBoard),
            SquadScout(squadBoard),
            ClusterTogether(squadBoard))
}
