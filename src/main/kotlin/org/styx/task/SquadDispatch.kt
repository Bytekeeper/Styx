package org.styx.task

import org.styx.Dispatch
import org.styx.Par
import org.styx.Styx
import org.styx.squad.*

val squadDispatch = Dispatch(
        "Squad Dispatch",
        { Styx.squads.squads }
) { squadBoard ->
    Par("Squad",
            false,
            LocalCombat(squadBoard),
            SeekCombatSquad(squadBoard),
            SquadBackOff(squadBoard),
            SquadScout(squadBoard),
            ClusterTogether(squadBoard))
}