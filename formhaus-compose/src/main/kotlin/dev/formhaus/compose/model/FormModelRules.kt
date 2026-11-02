package dev.formhaus.compose.model

import dev.formhaus.core.model.ConditionRule
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField
import dev.formhaus.core.renderer.FieldRules
import dev.formhaus.core.renderer.fieldRules

private fun ConditionRule?.fields(): List<String> = (this as? ConditionRule.When)?.conditions?.mapNotNull { it.field }.orEmpty()

internal fun FormModel.rules(
    field: FormField,
    key: String = field.key,
    options: List<FieldOption> = field.options.orEmpty(),
): FieldRules {
    val names = field.readOnly.fields() + options.flatMap { it.disabled.fields() }
    if (names.isNotEmpty()) {
        structureVersion
        val item = key.takeIf { it != field.key }?.substringBeforeLast('.')
        val group = item?.let { groupField(it.substringBeforeLast('.')) }
        for (name in names.distinct()) {
            val own = group?.items?.any { it.key == name } == true
            field(if (own) "$item.$name" else name).value
        }
    }
    return engine.fieldRules(field, key)
}
