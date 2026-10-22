package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.maxItems

internal class ItemLimits(val min: Double, val max: Double)

internal val FormField.itemLimits: ItemLimits
    get() {
        val required = validation?.required?.isTruthy == true
        return ItemLimits(validation?.minLength ?: if (required) 1.0 else 0.0, maxItems)
    }

internal fun createItem(fields: List<FormField>, source: JsonObject? = null): JsonObject =
    createValues(fields, source?.let(::Values)).json

internal fun createValues(
    fields: List<FormField>,
    initialValues: Values? = null,
    pick: (String, JsonObject) -> FormField? = { _, _ -> null },
): Values {
    val byKey = LinkedHashMap<String, MutableList<FormField>>()
    for (field in fields) byKey.getOrPut(field.key) { ArrayList() }.add(field)
    val values = Values()
    fun fill(field: FormField) {
        values.remove(field.key)
        val value = field.defaultValue
        val count = field.defaultItems
        if (value != null) {
            values.set(field.key, ValueSlot(value, Ref.preset(value, field.ordinal)))
        } else if (field.isGroup && count != null && count > 0) {
            val length = maxOf(0.0, minOf(count, field.itemLimits.max)).toInt()
            values.set(field.key, ValueSlot.fresh(JsonValue.Array(List(length) { JsonValue.Object(createItem(field.items)) })))
        }
    }
    val source = initialValues ?: Values()
    for (field in fields) if (byKey[field.key]?.last()?.ordinal == field.ordinal) fill(field)
    for (key in source.keys) if (key != "__proto__") values.set(key, source.slot(key))
    val expanded = HashSet<String>()
    for (field in fields) {
        val key = field.key
        val same = byKey[key] ?: continue
        val shared = same.size > 1
        val chosen = (if (shared) pick(key, values.json) else null) ?: same[0]
        val items = values.slot(key)
        val list = items.value?.arrayValue
        if (!source.has(key)) {
            values.remove(key)
            if (shared) fill(chosen) else if (items.value != null) values.set(key, items)
        } else if (chosen.isGroup && key !in expanded && list != null) {
            expanded.add(key)
            val mapped = list.map { item -> item.objectValue?.let { JsonValue.Object(createItem(chosen.items, it)) } ?: item }
            val ids = items.ref?.items.orEmpty()
            val itemIds = if (ids.size == list.size) ids else list.map { freshToken() }
            values.set(key, ValueSlot(JsonValue.Array(mapped), Ref(freshToken(), itemIds)))
        }
    }
    return values
}
