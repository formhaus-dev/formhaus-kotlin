package dev.formhaus.core.dirty

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.visibility.itemPath

internal fun sameValue(
    lhs: JsonValue?,
    rhs: JsonValue?,
): Boolean =
    when {
        lhs === rhs -> true
        lhs is JsonValue.Number && rhs is JsonValue.Number -> lhs.value == rhs.value
        lhs is JsonValue.Array && rhs is JsonValue.Array -> sameItems(lhs.items, rhs.items)
        lhs is JsonValue.Object && rhs is JsonValue.Object -> sameMembers(lhs.members, rhs.members)
        else -> lhs == rhs
    }

private fun sameItems(
    lhs: List<JsonValue>,
    rhs: List<JsonValue>,
): Boolean = lhs.size == rhs.size && lhs.indices.all { sameValue(lhs[it], rhs[it]) }

private fun sameMembers(
    lhs: JsonObject,
    rhs: JsonObject,
): Boolean = lhs.size == rhs.size && lhs.all { (key, value) -> sameValue(value, rhs[key]) }

private fun itemList(value: JsonValue?): List<JsonObject>? = value?.arrayValue?.let { items -> items.map { it.objectValue ?: return null } }

private fun MutableList<String>.addChanged(
    lhs: JsonObject,
    rhs: JsonObject,
    path: (String) -> String,
) {
    for ((key, value) in lhs) if (!sameValue(value, rhs[key])) add(path(key))
    for ((key, _) in rhs) if (key !in lhs) add(path(key))
}

private fun MutableList<String>.addChangedItems(
    key: String,
    before: JsonValue?,
    after: JsonValue?,
) {
    val old = itemList(before)
    val new = itemList(after)
    if (old == null || new == null || old.size != new.size) {
        add(key)
        return
    }
    for (index in old.indices) addChanged(old[index], new[index]) { itemPath(key, index, it) }
}

internal fun changedKeys(
    baseline: JsonObject,
    values: JsonObject,
): List<String> {
    val keys = ArrayList<String>()
    for ((key, value) in baseline) {
        val current = values[key]
        if (!sameValue(value, current)) keys.addChangedItems(key, value, current)
    }
    for ((key, value) in values) if (key !in baseline) keys.addChangedItems(key, null, value)
    keys.sort()
    return keys
}
