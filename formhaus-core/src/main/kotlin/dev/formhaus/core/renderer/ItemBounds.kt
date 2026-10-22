package dev.formhaus.core.renderer

import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.engine.ItemContext
import dev.formhaus.core.engine.Mapped
import dev.formhaus.core.engine.StepChangeReason
import dev.formhaus.core.engine.advance
import dev.formhaus.core.engine.errorKeys
import dev.formhaus.core.engine.itemLimits
import dev.formhaus.core.engine.optionName
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.jsTrimmed
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.validation.MAX_GROUP_ITEMS
import dev.formhaus.core.visibility.isBlank
import dev.formhaus.core.visibility.itemsOf

@ConsistentCopyVisibility
public data class ItemBounds internal constructor(
    public val min: Int,
    public val max: Int,
)

@RendererApi
public val FormEngine.version: Int get() = core.version

@RendererApi
public val FormEngine.errorsVersion: Int get() = core.errorsGeneration

@RendererApi
public val FormEngine.definitionRevision: Int get() = core.definitionGeneration

@RendererApi
public fun FormEngine.visibleNodes(step: FormStep): List<FormNode> = core.visibility.shownNodes(step.fields, core.values.json)

@RendererApi
public suspend fun FormEngine.autoAdvance(): Boolean = advance(StepChangeReason.AUTO_ADVANCE)

@RendererApi
public fun FormEngine.optionKey(key: String): String = optionName(key)

@RendererApi
public fun FormEngine.itemPath(key: String): ItemPath? = core.visibility.parse(key)?.let { ItemPath(it.group, it.index, it.field) }

@RendererApi
public fun FormEngine.value(key: String): JsonValue? {
    val path = itemPath(key) ?: return core.values[key]
    return itemsOf(core.values[path.group]).getOrNull(path.index)?.objectValue?.get(path.field)
}

@RendererApi
public fun FormEngine.hasValue(key: String): Boolean = value(key).let { it != null && it != JsonValue.String("") }

@RendererApi
public fun FormEngine.itemLimits(group: FormField): ItemBounds {
    val limits = group.itemLimits
    return ItemBounds(
        clampedInt(limits.min, 0..1_000_000) ?: 0,
        clampedInt(limits.max, 0..MAX_GROUP_ITEMS) ?: MAX_GROUP_ITEMS,
    )
}

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

@RendererApi
public fun FormEngine.firstErrorKey(): String? {
    val errors = core.errors
    if (errors.isEmpty()) return null
    return errorKeys(currentStep?.fields ?: definition.fields.orEmpty(), errors, core.values.json).firstOrNull()
}

private fun sectionName(key: String): String =
    buildString {
        for (unit in key) {
            if (unit in 'a'..'z' || unit in '0'..'9') append(unit) else append("_${unit.code.toString(16)}_")
        }
    }

@RendererApi
public fun FormEngine.autocomplete(
    field: FormField,
    at: String? = null,
): String? {
    val written = field.autocomplete?.stringValue ?: return null
    val token = written.jsTrimmed
    val lower = token.lowercase()
    val parts = at?.let(core.visibility::parse)
    if (parts == null || token.isEmpty() || lower == "on" || lower == "off") return written
    val section = "section-${sectionName(parts.group)}-${parts.index}"
    return if (lower.startsWith("section-")) "$section-${token.substring(8)}" else "$section $token"
}
