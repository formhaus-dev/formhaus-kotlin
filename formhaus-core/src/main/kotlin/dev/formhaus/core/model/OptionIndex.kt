package dev.formhaus.core.model

import dev.formhaus.core.json.JsonValue

internal class OptionIndex(
    options: List<FieldOption>,
) {
    private val positions = HashMap<Any, Int>()

    init {
        options.forEachIndexed { position, option ->
            optionKey(option.json["value"])?.let { positions.putIfAbsent(it, position) }
        }
    }

    fun position(value: JsonValue): Int? = optionKey(value)?.let(positions::get)
}

private fun optionKey(value: JsonValue?): Any? =
    when (value) {
        is JsonValue.String -> value.value
        is JsonValue.Bool -> value.value
        is JsonValue.Number -> value.value.takeUnless { it.isNaN() }?.let { if (it == 0.0) 0.0 else it }
        else -> null
    }
