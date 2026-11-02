package dev.formhaus.core.renderer

import dev.formhaus.core.engine.EngineCore
import dev.formhaus.core.engine.FormEngine
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField
import dev.formhaus.core.visibility.conditionsMet
import dev.formhaus.core.visibility.itemScope
import dev.formhaus.core.visibility.itemsOf

@RendererApi
public class FieldRules internal constructor(
    core: EngineCore,
    field: FormField,
    key: String,
) {
    private val limits by lazy { core.visibility.limits(core.values.json) }
    private val scope: JsonObject by lazy { ruleScope(core, key) }

    public val readOnly: Boolean = field.readOnly != null && conditionsMet(field.readOnly, scope, limits)

    public fun isDisabled(option: FieldOption): Boolean = option.disabled != null && conditionsMet(option.disabled, scope, limits)
}

private fun ruleScope(
    core: EngineCore,
    key: String,
): JsonObject {
    val visibility = core.visibility
    val values = visibility.conditionValues(core.values.json)
    val path = visibility.parse(key) ?: return values
    val group = visibility.groups.firstOrNull { it.key == path.group } ?: return values
    val item = itemsOf(core.values[group.key]).getOrNull(path.index) ?: return values
    return itemScope(group, item, values)
}

@RendererApi
public fun FormEngine.fieldRules(
    field: FormField,
    key: String = field.key,
): FieldRules = FieldRules(core, field, key)
