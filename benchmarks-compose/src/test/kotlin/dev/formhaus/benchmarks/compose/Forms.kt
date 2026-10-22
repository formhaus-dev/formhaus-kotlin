package dev.formhaus.benchmarks.compose

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import dev.formhaus.benchmarks.parseJson
import dev.formhaus.benchmarks.parseObject
import dev.formhaus.compose.model.FormModel
import dev.formhaus.core.model.FormDefinition

fun formModel(
    definition: String,
    values: String = "{}",
): FormModel = FormModel(FormDefinition(parseJson(definition)), parseObject(values), onSubmit = { _, _ -> null })

class CompositionCounter : CompositionTracer {
    private val counts = mutableMapOf<String, Int>()

    override fun traceEventStart(
        key: Int,
        dirty1: Int,
        dirty2: Int,
        info: String,
    ) {
        val name = info.substringBefore(" (")
        counts[name] = (counts[name] ?: 0) + 1
    }

    override fun traceEventEnd() = Unit

    override fun isTraceInProgress(): Boolean = true

    fun <T> record(action: () -> T): Map<String, Int> {
        counts.clear()
        Composer.setTracer(this)
        try {
            action()
        } finally {
            Composer.setTracer(null)
        }
        return counts.toMap()
    }
}
