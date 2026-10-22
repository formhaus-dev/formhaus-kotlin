package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FormField
import dev.formhaus.core.visibility.ListLimits
import dev.formhaus.core.visibility.isVisible
import dev.formhaus.core.visibility.itemPath
import dev.formhaus.core.visibility.itemScope
import dev.formhaus.core.visibility.itemsOf
import dev.formhaus.core.visibility.splitPath

private class ReconciledItem(val item: JsonValue, val cleared: List<String>, val stable: Boolean)

private fun VisibilityState.reconcileItem(fields: List<FormField>, item: JsonValue, id: Int?, scope: JsonObject, limits: ListLimits): ReconciledItem {
    var entry: JsonObject = item.objectValue ?: return ReconciledItem(item, emptyList(), true)
    val cleared = ArrayList<String>()
    repeat(fields.size + 1) {
        val inner = itemScope(fields, JsonValue.Object(entry), scope)
        val shown = fields.filter { isVisible(it, inner, limits) }
        val visible = shown.mapTo(HashSet()) { it.ordinal }
        val hidden = fields.filter { it.ordinal !in visible && entry[it.key] != null }
        val restored = MutableJsonObject()
        for (field in shown) {
            if (entry[field.key] != null) continue
            starts.itemStartValue(id, field.key)?.let { restored[field.key] = it }
        }
        if (hidden.isEmpty() && restored.isEmpty()) return ReconciledItem(JsonValue.Object(entry), cleared, true)
        val previous = entry
        val next = entry.merging(restored)
        for (field in hidden) {
            previous[field.key]?.let { starts.rememberItem(id, field.key, it) }
            next.remove(field.key)
        }
        entry = next
        cleared.addAll(hidden.map { it.key } + restored.keys)
    }
    return ReconciledItem(JsonValue.Object(entry), cleared, false)
}

internal fun VisibilityState.reconcileItems(values: Values, errors: MutableStringMap<String>, cleared: MutableSet<String>) {
    for (group in groups) {
        if (activeField(group.key, values.json)?.ordinal != group.ordinal) continue
        val source = itemsOf(values[group.key])
        val ids = values.itemIds(group.key)
        val scope = scope(values.json)
        val limits = limits(values.json)
        val fields = group.items
        val context = reconciled.context(group, scope, limits, starts.itemEpoch)
        val mapped = reconciled.map(group, source, ids, context) { position, item ->
            val result = reconcileItem(fields, item, ids.getOrNull(position), scope, limits)
            for (key in result.cleared) cleared.add(itemPath(group.key, position, key))
            Mapped(result.item, result.stable && result.cleared.isEmpty())
        }
        if (mapped.reused) continue
        if (mapped.items != source) values.set(group.key, ValueSlot(JsonValue.Array(mapped.items), Ref(freshToken(), ids)))
        dropHiddenErrors(group, mapped.items, values.json, errors, cleared)
    }
}

private fun VisibilityState.dropHiddenErrors(
    group: FormField,
    items: List<JsonValue>,
    values: JsonObject,
    errors: MutableStringMap<String>,
    cleared: MutableSet<String>,
) {
    if (errors.isEmpty()) return
    val fields = group.items
    val scope = scope(values)
    val limits = limits(values)
    val shown = HashMap<Int, Set<String>>()
    for (key in errors.keys) {
        val (position, field) = splitPath(group.key, key) ?: continue
        if (position < items.size) {
            val keys = shown.getOrPut(position) {
                fields.filter { isVisible(it, itemScope(fields, items[position], scope), limits) }.mapTo(HashSet()) { it.key }
            }
            if (field in keys) continue
        }
        errors.remove(key)
        cleared.add(key)
    }
}
