package org.styx

import bwapi.UnitType
import org.styx.Context.find
import org.styx.Context.self
import org.styx.tasks.BuildTask
import org.styx.tasks.TrainTask

val test = MParallel(
        BuildTask(2, UnitType.Zerg_Spawning_Pool),
        Repeat(
                If({ find.myUnits.count { it.isCompleted && it.type == UnitType.Zerg_Zergling } < 10 },
                        Parallel(
                                MappedTasks({ find.my(UnitType.Zerg_Larva) }, {
                                    TrainTask(2, UnitType.Zerg_Zergling)
                                }))
                )),
        Repeat(TrainTask(2, UnitType.Zerg_Drone)),
//        Repeat(If({ find.myUnits.count { it.type == UnitType.Zerg_Zergling } + 10 < 2 * find.myUnits.count { it.type.isWorker } }, TrainTask(3, UnitType.Zerg_Zergling), Running)),
        Repeat(If({ self.supplyTotal() - self.supplyUsed() < find.myTrainers.count { it.isCompletedTrainer } * 4 && self.supplyTotal() < 400 }, TrainTask(3, UnitType.Zerg_Overlord))),
        Repeat(If({ find.myWorkers.size / 5 > find.myTrainers.count { it.isCompletedTrainer } }, BuildTask(3, UnitType.Zerg_Hatchery)))
)