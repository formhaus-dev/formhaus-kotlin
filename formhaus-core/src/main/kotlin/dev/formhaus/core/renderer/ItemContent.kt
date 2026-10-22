package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.ItemContext
import dev.formhaus.core.engine.Mapped
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.jsTrimmed
import dev.formhaus.core.model.FormField
import dev.formhaus.core.visibility.isBlank
import dev.formhaus.core.visibility.itemsOf

private fun hasContent(
    fields: List<FormField>,
    item: JsonValue,
): Boolean {
    val entry = item.objectValue ?: return false
    return fields.any { field ->
        val raw = entry[field.key]
        val value = raw?.stringValue?.let { JsonValue.String(it.jsTrimmed) } ?: raw
        !isBlank(value) && value != JsonValue.Bool(false) && value != field.defaultValue
    }
}

private fun FormEngine.group(key: String): FormField? = core.visibility.groups.firstOrNull { it.key == key }

@RendererApi
public fun FormEngine.itemHasContent(
    key: String,
    index: Int,
): Boolean {
    val group = group(key) ?: return false
    val item = itemsOf(core.values[key]).getOrNull(index) ?: return false
    return hasContent(group.items, item)
}

@RendererApi
public fun FormEngine.itemsHaveContent(key: String): List<Boolean> {
    val group = group(key) ?: return emptyList()
    val fields = group.items
    return core.visibility.filled
        .map(
            group,
            itemsOf(core.values[key]),
            context = ItemContext.FIXED,
            sharing = true,
        ) { _, item ->
            Mapped(hasContent(fields, item), true)
        }.items
}
