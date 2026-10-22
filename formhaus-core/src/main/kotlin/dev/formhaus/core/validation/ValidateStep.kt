package dev.formhaus.core.validation

import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.model.valueFields
import dev.formhaus.core.regex.CallBudget
import dev.formhaus.core.visibility.ListLimits
import dev.formhaus.core.visibility.isVisible
import dev.formhaus.core.visibility.itemPath
import dev.formhaus.core.visibility.itemScope
import dev.formhaus.core.visibility.itemsOf
import dev.formhaus.core.visibility.shownFields

public fun validateStep(
    step: FormStep,
    values: JsonObject,
    options: ValidateOptions = ValidateOptions(),
): FieldErrors = validateFields(step.fields, values, options)

public fun validateFields(
    fields: List<FormNode>,
    values: JsonObject,
    options: ValidateOptions = ValidateOptions(),
): FieldErrors =
    CallBudget.run {
        validateShown(fields, values, values, ShownChecks(options.validators, options.messages, ListLimits.STANDARD))
    }

internal fun customRules(nodes: List<FormNode>): Boolean =
    valueFields(nodes).any { field -> (listOf(field) + field.items).any { it.validation?.validator != null } }

internal class ShownChecks(
    val validators: Validators,
    val messages: MessageSource?,
    val limits: ListLimits,
) {
    val options: ValidateOptions get() = ValidateOptions(validators, messages)
}

private fun validateItems(
    group: FormField,
    values: JsonObject,
    scope: JsonObject,
    checks: ShownChecks,
    errors: MutableStringMap<String>,
) {
    itemsOf(values[group.key]).forEachIndexed { index, item ->
        val own = itemScope(group, item, values)
        val shown = itemScope(group, item, scope)
        for (field in group.items) {
            if (!isVisible(field, shown, checks.limits)) continue
            val error = validateFieldValue(field, own[field.key], own, checks.options) ?: continue
            errors[itemPath(group.key, index, field.key)] = error
        }
    }
}

internal fun validateShown(
    fields: List<FormNode>,
    values: JsonObject,
    scope: JsonObject,
    checks: ShownChecks,
): FieldErrors {
    val errors = MutableStringMap<String>()
    for (field in shownFields(fields, scope, checks.limits)) {
        validateFieldValue(field, values[field.key], values, checks.options)?.let { errors[field.key] = it }
        if (field.isGroup && typeError(field, values[field.key]) == null) validateItems(field, values, scope, checks, errors)
    }
    return errors
}
