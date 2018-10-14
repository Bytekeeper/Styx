package org.fttbot.task

import org.openbw.bwapi4j.Position
import org.openbw.bwapi4j.unit.MobileUnit

class SafeMove(val unit: MobileUnit, val to: Position = unit.position, tolerance: Int = 32, utility: Double = 1.0)
    : Sequence(AvoidCombat(unit, utility), Move(unit, to, tolerance, utility))