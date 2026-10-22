package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.trimValue
import dev.formhaus.core.visibility.ListLimits
import dev.formhaus.core.visibility.cleanItems

private fun StartValues.keptItems(group: FormField, items: List<JsonValue>, ids: List<Int>): List<JsonValue> =
    items.mapIndexed { position, item ->
        val entry = item.objectValue ?: return@mapIndexed item
        val missing = group.items.mapNotNull { field ->
            if (entry[field.key] != null) null else itemStartValue(ids.getOrNull(position), field.key)?.let { field.key to it }
        }
        if (missing.isEmpty()) item else JsonValue.Object(entry.toMutableStringMap().also { filled -> missing.forEach { (key, value) -> filled[key] = value } })
    }

internal fun VisibilityState.withKept(values: Values, skipped: List<String>): Values {
    if (!keep) return values
    val result = values.copy()
    for (key in index.fieldsByKey.keys) {
        if (result[key] != null || key in skipped || activeField(key, values.json) != null) continue
        index.fieldsByKey[key]?.firstOrNull()?.let { result.set(key, starts.startValue(it)) }
    }
    for (group in groups) {
        if (group.key in skipped) continue
        val items = result[group.key]?.arrayValue ?: continue
        val ids = result.itemIds(group.key)
        val filled = starts.keptItems(group, items, ids)
        if (filled != items) result.set(group.key, ValueSlot(JsonValue.Array(filled), Ref(freshToken(), ids)))
    }
    return result
}

internal fun EngineCore.keptValues(): Values = visibility.withKept(values, skipped.flatMap { it.keys })

internal fun EngineCore.keptJson(): JsonObject {
    keptCache?.takeIf { it.version == version && it.generation == valuesGeneration }?.let { return it.values }
    return keptValues().json.also { keptCache = KeptCache(version, valuesGeneration, it) }
}

internal class KeptCache(val version: Int, val generation: Int, val values: JsonObject)

internal fun EngineCore.addKept(result: MutableJsonObject) {
    val kept = keptValues().json
    for (field in visibility.allFields) {
        val value = kept[field.key] ?: continue
        val owner = visibility.fieldOf(field.key, values.json) ?: continue
        result.assign(field.key, if (owner.isGroup) cleanItems(owner, value, MutableJsonObject(), ListLimits.STANDARD, all = true) else trimValue(owner, value))
    }
}
