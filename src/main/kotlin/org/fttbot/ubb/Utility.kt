package org.fttbot.ubb

interface Utility {
    val utility: Double
    fun process()
}

typealias UtilityProvider = () -> List<Utility>