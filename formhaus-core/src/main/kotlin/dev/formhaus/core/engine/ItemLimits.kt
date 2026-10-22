package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.maxItems

internal class ItemLimits(
    val min: Double,
    val max: Double,
)

internal val FormField.itemLimits: ItemLimits
    get() {
        val required = validation?.required?.isTruthy == true
        return ItemLimits(validation?.minLength ?: if (required) 1.0 else 0.0, maxItems)
    }

internal fun createItem(
    fields: List<FormField>,
    source: JsonObject? = null,
): JsonObject = createValues(fields, source?.let(::Values)).json

internal fun createValues(
    fields: List<FormField>,
    initialValues: Values? = null,
    pick: (String, JsonObject) -> FormField? = { _, _ -> null },
): Values {
    val byKey = LinkedHashMap<String, MutableList<FormField>>()
    for (field in fields) byKey.getOrPut(field.key) { ArrayList() }.add(field)
    val values = Values()
    val source = initialValues ?: Values()
    for (field in fields) if (byKey[field.key]?.last()?.ordinal == field.ordinal) values.fill(field)
    for (key in source.keys) if (key != "__proto__") values.set(key, source.slot(key))
    val expanded = HashSet<String>()
    for (field in fields) {
        val same = byKey[field.key] ?: continue
        values.reconcile(field.key, same, source, pick, expanded)
    }
    return values
}

private fun Values.fill(field: FormField) {
    remove(field.key)
    val value = field.defaultValue
    val count = field.defaultItems
    if (value != null) {
        set(field.key, ValueSlot(value, Ref.preset(value, field.ordinal)))
    } else if (field.isGroup && count != null && count > 0) {
        val length = maxOf(0.0, minOf(count, field.itemLimits.max)).toInt()
        set(field.key, ValueSlot.fresh(JsonValue.Array(List(length) { JsonValue.Object(createItem(field.items)) })))
    }
}

private fun Values.reconcile(
    key: String,
    same: List<FormField>,
    source: Values,
    pick: (String, JsonObject) -> FormField?,
    expanded: MutableSet<String>,
) {
    val shared = same.size > 1
    val chosen = (if (shared) pick(key, json) else null) ?: same[0]
    val items = slot(key)
    val list = items.value?.arrayValue
    if (!source.has(key)) {
        remove(key)
        if (shared) {
            fill(chosen)
        } else if (items.value != null) {
            set(key, items)
        }
    } else if (list != null && chosen.isGroup && expanded.add(key)) {
        set(key, expandItems(chosen, items, list))
    }
}

private fun expandItems(
    chosen: FormField,
    items: ValueSlot,
    list: List<JsonValue>,
): ValueSlot {
    val mapped = list.map { item -> item.objectValue?.let { JsonValue.Object(createItem(chosen.items, it)) } ?: item }
    val ids = items.ref?.items.orEmpty()
    val itemIds = if (ids.size == list.size) ids else list.map { freshToken() }
    return ValueSlot(JsonValue.Array(mapped), Ref(freshToken(), itemIds))
}
