package dev.formhaus.benchmarks

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.validateFieldValue

class RegexCase(
    val id: String,
    val label: String,
    val pattern: String,
    val short: String,
    val long: String,
)

object RegexCases {
    private val root: JsonValue by lazy {
        val stream = requireNotNull(RegexCases::class.java.getResourceAsStream("/regex-cases.json"))
        JsonValue.parse(stream.use { it.readBytes() })
    }

    val typical: List<RegexCase> by lazy { cases("typical") }

    val pathological: List<RegexCase> by lazy { cases("pathological") }

    fun typical(id: String): RegexCase = typical.first { it.id == id }

    fun pathological(id: String): RegexCase = pathological.first { it.id == id }

    private fun cases(group: String): List<RegexCase> =
        requireNotNull(root[group]?.arrayValue).map { case ->
            RegexCase(
                id = requireNotNull(case["id"]?.stringValue),
                label = requireNotNull(case["label"]?.stringValue),
                pattern = text(case["pattern"]),
                short = text(case["short"]),
                long = text(case["long"]),
            )
        }

    private fun text(spec: JsonValue?): String {
        spec?.stringValue?.let { return it }
        return spec?.arrayValue.orEmpty().joinToString("") { part ->
            part.stringValue
                ?: part["random"]?.numberValue?.let { randomText(it.toInt(), requireNotNull(part["seed"]?.numberValue).toLong()) }
                ?: requireNotNull(part["repeat"]?.stringValue).repeat(requireNotNull(part["count"]?.numberValue).toInt())
        }
    }
}

fun randomText(
    count: Int,
    seed: Long,
): String {
    var state = seed
    return buildString(count) {
        repeat(count) {
            state = state * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            append(if (state ushr 63 == 0L) 'a' else 'b')
        }
    }
}

fun patternField(pattern: String): FormField {
    val field =
        stringMapOf(
            "key" to JsonValue.String("value"),
            "type" to JsonValue.String("text"),
            "label" to JsonValue.String("Value"),
            "validation" to JsonValue.Object(stringMapOf("pattern" to JsonValue.String(pattern))),
        )
    val definition =
        stringMapOf(
            "id" to JsonValue.String("bench"),
            "submit" to JsonValue.Object(stringMapOf("label" to JsonValue.String("Send"))),
            "fields" to JsonValue.Array(listOf(JsonValue.Object(field))),
        )
    return FormDefinition(JsonValue.Object(definition)).fields.orEmpty().first() as FormField
}

fun validatePattern(
    field: FormField,
    text: String,
): String? = validateFieldValue(field, JsonValue.String(text), stringMapOf())
