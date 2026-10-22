package dev.formhaus.core.support

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject

sealed class RawJson {
    data object Null : RawJson()

    data class Bool(
        val value: Boolean,
    ) : RawJson()

    data class Number(
        val value: Double,
    ) : RawJson()

    data class Text(
        val text: String,
    ) : RawJson()

    data class Array(
        val elements: List<RawJson>,
    ) : RawJson()

    data class Object(
        val members: List<Pair<String, RawJson>>,
    ) : RawJson()

    operator fun get(key: String): RawJson? = (this as? Object)?.members?.lastOrNull { it.first == key }?.second

    val units: String? get() = (this as? Text)?.text

    val number: Double? get() = (this as? Number)?.value

    val items: List<RawJson> get() = (this as? Array)?.elements.orEmpty()

    fun value(dropped: String = ""): JsonValue? =
        when (this) {
            Null -> {
                JsonValue.Null
            }

            is Bool -> {
                JsonValue.Bool(value)
            }

            is Number -> {
                JsonValue.Number(value)
            }

            is Text -> {
                text.takeIf(::isWellFormed)?.let(JsonValue::String)
            }

            is Array -> {
                elements.map { it.value() ?: return null }.let(JsonValue::Array)
            }

            is Object -> {
                val result = MutableJsonObject()
                for ((key, member) in members) {
                    if (key == dropped) continue
                    if (!isWellFormed(key)) return null
                    result[key] = member.value() ?: return null
                }
                JsonValue.Object(result)
            }
        }
}

private fun isWellFormed(text: String): Boolean {
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char.isHighSurrogate() -> {
                if (index + 1 >= text.length || !text[index + 1].isLowSurrogate()) return false
                index += 2
            }

            char.isLowSurrogate() -> {
                return false
            }

            else -> {
                index += 1
            }
        }
    }
    return true
}
