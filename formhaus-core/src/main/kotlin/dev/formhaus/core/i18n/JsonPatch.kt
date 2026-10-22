package dev.formhaus.core.i18n

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject

internal class JsonPatch {
    private val writes = LinkedHashMap<String, JsonValue>()
    private val children = LinkedHashMap<String, JsonPatch>()

    fun set(
        location: List<String>,
        key: String,
        value: JsonValue,
    ) {
        location.fold(this) { patch, token -> patch.children.getOrPut(token, ::JsonPatch) }.writes[key] = value
    }

    fun applied(target: JsonValue): JsonValue =
        when (target) {
            is JsonValue.Object -> {
                val result = target.members.toMutableStringMap()
                for ((key, value) in writes) result[key] = value
                for ((token, patch) in children) {
                    result[token] =
                        patch.applied(result[token] ?: JsonValue.Object(MutableJsonObject()))
                }
                JsonValue.Object(result)
            }

            is JsonValue.Array -> {
                val items = target.items.toMutableList()
                for ((token, patch) in children) {
                    val index = token.toIntOrNull()?.takeIf { it in items.indices } ?: continue
                    items[index] = patch.applied(items[index])
                }
                JsonValue.Array(items)
            }

            else -> {
                target
            }
        }
}
