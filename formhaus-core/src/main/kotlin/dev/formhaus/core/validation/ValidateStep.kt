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
    options: ValidateOptions = defaultOptions,
): FieldErrors = validateFields(step.fields, values, options)

public fun validateFields(
    fields: List<FormNode>,
    values: JsonObject,
    options: ValidateOptions = defaultOptions,
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

internal fun visitShown(
    fields: List<FormNode>,
    values: JsonObject,
    scope: JsonObject,
    limits: ListLimits,
    visit: (String, FormField, JsonObject) -> Unit,
) {
    for (field in shownFields(fields, scope, limits)) {
        visit(field.key, field, values)
        if (!field.isGroup || typeError(field, values[field.key]) != null) continue
        itemsOf(values[field.key]).forEachIndexed { index, item ->
            val own = itemScope(field, item, values)
            val shown = itemScope(field, item, scope)
            for (rule in field.items) if (isVisible(rule, shown, limits)) visit(itemPath(field.key, index, rule.key), rule, own)
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
    visitShown(fields, values, scope, checks.limits) { path, field, own ->
        validateFieldValue(field, own[field.key], own, checks.options)?.let { errors[path] = it }
    }
    return errors
}
