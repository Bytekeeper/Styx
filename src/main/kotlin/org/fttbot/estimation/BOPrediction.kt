package org.fttbot.estimation

import de.daslaboratorium.machinelearning.classifier.Classification
import de.daslaboratorium.machinelearning.classifier.bayes.BayesClassifier
import org.openbw.bwapi4j.type.UnitType
import org.openbw.bwapi4j.unit.PlayerUnit

object BOPrediction {
    private val classifier = BayesClassifier<Event, UnitType>()
    private val seenUnits = HashMap<UnitType, MutableSet<PlayerUnit>>()

    fun predict() : Classification<Event, UnitType>? = classifier.classify(eventsFromSeenUnits())

    fun onSeenUnit(unit: PlayerUnit) {
        val units = seenUnits.computeIfAbsent(unit.initialType) { HashSet() }
        if (units.add(unit)) {
            classifier.learn(unit.initialType, eventsFromSeenUnits())
        }
    }

    private fun eventsFromSeenUnits(): List<Event> {
        return seenUnits.map { (k, v) ->
            Event(when {
                v.size < 5 -> Amount.SMALL
                v.size < 10 -> Amount.MEDIUM
                else -> Amount.LARGE
            }, k)
        }
    }

    data class Event(val amount: Amount, val unitType: UnitType)
    enum class Amount {
        SMALL, MEDIUM, LARGE
    }
}