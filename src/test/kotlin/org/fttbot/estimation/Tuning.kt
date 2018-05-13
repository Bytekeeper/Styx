package org.fttbot.estimation

import java.lang.reflect.Modifier

object Tuning {
    @JvmStatic
    fun main(args: Array<String>) {

        CombatEvalTest.setup()
        val test = CombatEvalTest()
        var bestHits = 0
        for (a in 3..20) {
            CombatEval.amountPower = a * 0.1
            for (sp in 3..20) {
                CombatEval.strengthPower = sp * 0.1
                for (es in 1..100) {
                    CombatEval.evalScale = es * 0.01
                    val hits = CombatEvalTest::class.java.methods
                            .filter { it.returnType == Void.TYPE && it.parameterCount == 0 && !Modifier.isStatic(it.modifiers) }
                            .count {
                                try {
                                    it.invoke(test); true
                                } catch (e: Exception) {
                                    false
                                }
                            }
                    if (hits > bestHits) {
                        bestHits = hits
                        println("hits: $bestHits, a: $a, sp: $sp, es: $es")
                    }
                }
            }
        }

        println(bestHits)
    }
}