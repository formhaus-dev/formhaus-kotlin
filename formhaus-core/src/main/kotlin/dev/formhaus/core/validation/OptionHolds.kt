package dev.formhaus.core.validation

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.json.sameValueZero
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.optionKey
import dev.formhaus.core.visibility.ListLimits
import dev.formhaus.core.visibility.conditionsMet
import dev.formhaus.core.visibility.itemsOf

private fun holds(
    value: JsonValue?,
    candidate: JsonValue,
): Boolean = value?.arrayValue?.any { sameValueZero(it, candidate) } ?: sameValueZero(value, candidate)

internal fun heldOut(
    field: FormField,
    value: JsonValue,
    values: JsonObject,
    previous: JsonValue?,
    limits: ListLimits,
): Boolean {
    val ruled = field.ruledOptions
    if (ruled.isEmpty()) return false
    val chosen = value.arrayValue ?: listOf(value)
    return ruled.any { option ->
        val candidate = option.json["value"] ?: return@any false
        chosen.any { sameValueZero(it, candidate) } &&
            !holds(previous, candidate) &&
            !holds(field.defaultValue, candidate) &&
            conditionsMet(option.disabled, values, limits)
    }
}

private fun occurrences(
    item: JsonValue,
    key: String,
): List<JsonValue> {
    val value = item.objectValue?.get(key) ?: return emptyList()
    return value.arrayValue ?: listOf(value)
}

internal class ItemHolds(
    group: FormField,
    previous: JsonObject?,
    items: List<JsonValue>,
) {
    private val held: List<JsonObject> = if (previous == null) emptyList() else holds(group, previous, items)

    fun previous(
        index: Int,
        field: FormField,
    ): JsonValue? = held.getOrNull(index)?.get(field.key)

    private companion object {
        fun holds(
            group: FormField,
            previous: JsonObject,
            items: List<JsonValue>,
        ): List<JsonObject> {
            val counts = group.items.filter { it.ruledOptions.isNotEmpty() }.map { it.key to stored(it, previous[group.key]) }
            if (counts.isEmpty()) return emptyList()
            return items.map { item ->
                MutableStringMap<JsonValue>().also { entry ->
                    for ((key, left) in counts) entry[key] = JsonValue.Array(take(left, occurrences(item, key)))
                }
            }
        }

        fun stored(
            field: FormField,
            group: JsonValue?,
        ): HashMap<Any, Int> {
            val disabled = field.ruledOptions.mapNotNullTo(HashSet()) { optionKey(it.json["value"]) }
            val left = HashMap<Any, Int>()
            for (item in itemsOf(group)) {
                for (value in occurrences(item, field.key)) {
                    val key = optionKey(value)?.takeIf { it in disabled } ?: continue
                    left[key] = (left[key] ?: 0) + 1
                }
            }
            return left
        }

        fun take(
            left: HashMap<Any, Int>,
            values: List<JsonValue>,
        ): List<JsonValue> {
            val keys = values.map(::optionKey)
            val kept = values.filterIndexed { index, _ -> (keys[index]?.let(left::get) ?: 0) > 0 }
            for (key in keys) {
                val count = key?.let(left::get) ?: continue
                if (count > 0) left[key] = count - 1
            }
            return kept
        }
    }
}
