package dev.formhaus.benchmarks

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.FormEngineOptions
import dev.formhaus.core.engine.RecallFormatFn
import dev.formhaus.core.model.FormField
import dev.formhaus.core.renderer.RecalledTexts
import dev.formhaus.core.renderer.recalledTexts
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Setup

private val stored: RecallFormatFn = { answer, _, _ -> answer.stored }

private class RecallTarget(
    format: RecallFormatFn?,
    index: Int,
) {
    val engine =
        FormEngine(parseJson(BenchmarkForms.recall), parseObject(BenchmarkForms.RECALL_ANSWERS), FormEngineOptions(formatRecall = format))
    val field = requireNotNull(engine.definition.steps)[1].fields[index] as FormField

    fun texts(): RecalledTexts = engine.recalledTexts(field)
}

open class RecallBenchmark : JmhDefaults() {
    private lateinit var plain: RecallTarget
    private lateinit var asStored: RecallTarget
    private lateinit var formatted: RecallTarget

    @Setup
    fun setUp() {
        plain = RecallTarget(null, 0)
        asStored = RecallTarget(stored, 1)
        formatted = RecallTarget(null, 1)
    }

    @Benchmark
    fun textWithoutTokens(): RecalledTexts = plain.texts()

    @Benchmark
    fun fourAnswersAsStored(): RecalledTexts = asStored.texts()

    @Benchmark
    fun fourAnswersFormatted(): RecalledTexts = formatted.texts()
}
