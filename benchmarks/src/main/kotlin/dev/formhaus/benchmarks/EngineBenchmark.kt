package dev.formhaus.benchmarks

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.validation.validateStep
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Setup

open class EngineBenchmark : JmhDefaults() {
    private lateinit var definition: JsonValue
    private lateinit var answers: JsonObject
    private lateinit var engine: FormEngine
    private lateinit var contact: FormStep
    private val names = listOf(JsonValue.String("Ada Lovelace"), JsonValue.String("Grace Hopper"))
    private var next = 0

    @Setup
    fun setUp() {
        definition = parseJson(BenchmarkForms.inquiry)
        answers = parseObject(BenchmarkForms.inquiryAnswers)
        engine = FormEngine(definition, answers)
        contact = requireNotNull(engine.definition.steps).first { it.id == "contact" }
    }

    @Benchmark
    fun create(): FormEngine = FormEngine(definition, answers)

    @Benchmark
    fun setValue() {
        next = 1 - next
        engine.setValue("name", names[next])
    }

    @Benchmark
    fun validateStep(): FieldErrors = validateStep(contact, answers)
}
