package org.fttbot

import io.jenetics.*
import io.jenetics.engine.Engine
import io.jenetics.engine.EvolutionResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.function.Function

internal class ElizaTest {
    @Test
    fun yoMamaTest() {
        Eliza.reply(null, "Hey xyz Yo mama   so fat she left the house in high heels and when she came back she had on flip flops.")

        assertThat(Eliza.messages).anyMatch { it.text.contains("And yo") }
    }


    @Test
    fun tst() {
        val gtf = Genotype.of(IntegerChromosome.of(0, 10000, 2))

        val fn: Function<Genotype<IntegerGene>, Int> = Function {
            val chromosome = it.chromosome.`as`(IntegerChromosome::class.java)
            Math.abs(5 + chromosome.intValue(0) - chromosome.intValue(1))
        }
        val engine = Engine.builder(fn, gtf)
                .optimize(Optimize.MINIMUM)
                .build()
        val toBestGenotype = EvolutionResult.toBestEvolutionResult<IntegerGene, Int>()
        val result = engine.stream()
                .limit(200)
                .collect(toBestGenotype)
        println(result)
    }
}
