package dev.formhaus.core.server

import dev.formhaus.core.engine.VisibilityState
import dev.formhaus.core.engine.orderedKeys
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.ResolveMessage
import dev.formhaus.core.validation.Validators
import dev.formhaus.core.validation.fieldValueError
import dev.formhaus.core.validation.normalizeValue
import dev.formhaus.core.validation.typeError
import dev.formhaus.core.visibility.itemPath
import dev.formhaus.core.visibility.itemScope

internal class InvalidValue(val key: String, val value: JsonValue?)

internal class ItemError(val key: String, val error: String)

internal class ActiveValues(
    var values: MutableJsonObject = MutableJsonObject(),
    val invalid: List<InvalidValue> = emptyList(),
    active: List<Pair<String, FormField>> = emptyList(),
) {
    private val activeByKey = LinkedHashMap<String, FormField>().also { map -> for ((key, field) in active) map.putIfAbsent(key, field) }

    fun field(key: String): FormField? = activeByKey[key]
}

private fun withoutInvalid(item: JsonObject, fields: List<FormField>, resolve: ResolveMessage, report: (String, String) -> Unit): MutableJsonObject {
    val entry = item.toMutableStringMap()
    for (field in fields) {
        if (field.key !in item) continue
        val error = typeError(field, item[field.key], resolve) ?: continue
        report(field.key, error)
        entry.remove(field.key)
    }
    return entry
}

internal fun activeValues(visibility: VisibilityState, fields: List<FormField>, source: JsonObject, resolve: ResolveMessage): ActiveValues {
    val first = LinkedHashMap<String, FormField>()
    for (field in fields.asReversed()) first[field.key] = field
    val keys = orderedKeys(fields.map { it.key }).filter { it in source }
    var result = ActiveValues()
    repeat(fields.size + 1) {
        val fieldOf = { key: String -> result.field(key) ?: first[key] }
        val normalized = MutableJsonObject()
        for (key in keys) {
            val field = fieldOf(key) ?: continue
            normalizeValue(field, source[key])?.let { normalized[key] = it }
        }
        val invalid = ArrayList<InvalidValue>()
        val values = withoutInvalid(normalized, keys.mapNotNull(fieldOf), resolve) { key, _ -> invalid.add(InvalidValue(key, normalized[key])) }
        val active = keys.mapNotNull { key -> visibility.activeField(key, values)?.let { key to it } }
        val next = ActiveValues(values, invalid, active)
        val settled = keys.all { next.field(it)?.ordinal == result.field(it)?.ordinal }
        result = next
        if (settled) return result
    }
    return result
}

internal fun blankItems(resolved: ActiveValues, fields: List<FormField>, resolve: ResolveMessage): List<ItemError> {
    val errors = ArrayList<ItemError>()
    for (group in fields) {
        if (!group.isGroup || (resolved.field(group.key) ?: group).ordinal != group.ordinal) continue
        val key = group.key
        val items = resolved.values[key]?.arrayValue ?: continue
        val cleaned = items.mapIndexed { index, item ->
            val entry = item.objectValue ?: return@mapIndexed item
            JsonValue.Object(withoutInvalid(entry, group.items, resolve) { field, error -> errors.add(ItemError(itemPath(key, index, field), error)) })
        }
        resolved.values = resolved.values.toMutableStringMap().also { it[key] = JsonValue.Array(cleaned) }
    }
    return errors
}

internal fun keepHidden(fields: List<FormField>, resolved: ActiveValues, submitted: MutableJsonObject, skipped: List<String>, validators: Validators) {
    val source = resolved.values
    val scope = source.merging(submitted)
    val valid = { field: FormField, value: JsonValue?, values: JsonObject -> fieldValueError(field, value, values, validators, null, required = false) == null }
    val items = { group: FormField, value: List<JsonValue>, kept: List<JsonValue> ->
        JsonValue.Array(
            kept.mapIndexed { index, item ->
                val entry = item.objectValue
                val from = value.getOrNull(index)?.objectValue
                if (entry == null || from == null) return@mapIndexed item
                val own = itemScope(group, value[index], scope)
                val filled = entry.toMutableStringMap()
                for (field in group.items) {
                    if (field.key in from && field.key !in entry && valid(field, from[field.key], own)) from[field.key]?.let { filled[field.key] = it }
                }
                JsonValue.Object(filled)
            },
        )
    }
    for (key in orderedKeys(fields.map { it.key })) {
        val value = source[key] ?: continue
        if (key in skipped) continue
        val field = resolved.field(key) ?: fields.firstOrNull { it.key == key } ?: continue
        val shown = key in submitted
        if (!shown && !valid(field, value, scope)) continue
        val kept = if (shown) submitted[key] else value
        val list = value.arrayValue
        val sent = kept?.arrayValue
        if (field.isGroup && list != null && sent != null) {
            submitted[key] = items(field, list, if (shown) sent else list.map { JsonValue.Object(MutableJsonObject()) })
        } else {
            submitted.assign(key, kept)
        }
    }
}
