package dev.formhaus.benchmarks

import dev.formhaus.core.json.JsonValue
import java.io.File

private val nanosPerUnit = mapOf("ns/op" to 1.0, "us/op" to 1e3, "ms/op" to 1e6, "s/op" to 1e9)

class JmhResults(
    file: File,
) {
    private val medians: Map<String, Double> =
        requireNotNull(JsonValue.parse(file.readBytes()).arrayValue).associate { result ->
            val name = requireNotNull(result["benchmark"]?.stringValue).split('.').takeLast(2).joinToString(".")
            val case = result["params"]?.get("case")?.stringValue
            val metric = requireNotNull(result["primaryMetric"])
            val scale = requireNotNull(nanosPerUnit[metric["scoreUnit"]?.stringValue])
            val samples =
                metric["rawData"]?.arrayValue.orEmpty().flatMap { fork ->
                    fork.arrayValue.orEmpty().mapNotNull { it.numberValue }
                }
            listOfNotNull(name, case).joinToString(":") to median(samples) * scale
        }

    operator fun get(key: String): Double? = medians[key]
}

class V8Results(
    file: File?,
) {
    private val rows: JsonValue? = file?.takeIf { it.exists() }?.let { JsonValue.parse(it.readBytes()) }

    operator fun get(key: String): String =
        rows?.get(key)?.let { value -> value.numberValue?.let(::formatNanos) ?: value.stringValue } ?: "n/a"
}
