package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.jsonStringify
import dev.formhaus.core.json.strictEquals
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.StepButton
import dev.formhaus.core.validation.booleanTypes
import dev.formhaus.core.validation.builtInTypes
import dev.formhaus.core.validation.listTypes
import dev.formhaus.core.validation.numberTypes

private val listOperators = listOf("in", "notIn")
private val comparisons = listOf("gt", "gte", "lt", "lte")

private fun expectedType(field: FormField): String? =
    when (field.type) {
        in numberTypes -> "number"
        in booleanTypes -> "boolean"
        else -> if (field.type in builtInTypes && field.type !in listTypes && field.type != "file") "string" else null
    }

private fun mismatch(
    name: String,
    path: String,
    problem: String,
    hint: String,
): List<DefinitionIssue> =
    listOf(
        warning(
            if (name in
                containsOperators
            ) {
                IssueCode.CONTAINS_NOT_LIST
            } else {
                IssueCode.CONDITION_VALUE_MISMATCH
            },
            path,
            problem,
            hint,
        ),
    )

private fun valueIssue(
    target: FormField,
    name: String,
    operand: JsonValue?,
    path: String,
): List<DefinitionIssue> {
    val contains = name in containsOperators
    val list = target.type in listTypes
    return when {
        name == "notEmpty" -> notEmptyIssue(operand, path)
        name in comparisons || (contains && !list) -> comparisonIssue(target, name, path, contains, list)
        list && !contains -> listComparisonIssue(target, name, path)
        else -> operandIssue(target, name, operand, path)
    }
}

private fun notEmptyIssue(
    operand: JsonValue?,
    path: String,
): List<DefinitionIssue> {
    if (operand?.isTruthy == true) return emptyList()
    val hint = "Use \"notEmpty\": true, or remove the condition"
    return listOf(warning(IssueCode.CONDITION_ALWAYS_TRUE, path, "\"notEmpty\": false matches every value", hint))
}

private fun comparisonIssue(
    target: FormField,
    name: String,
    path: String,
    contains: Boolean,
    list: Boolean,
): List<DefinitionIssue> {
    val checked =
        if (contains) {
            target.type in builtInTypes || target.isGroup
        } else {
            list || target.type in booleanTypes
        }
    if (!checked) return emptyList()
    val result = if (name == "notContains") "always" else "never"
    val hint = if (contains) "Use eq or in, or test a multiselect field" else "Compare a number, text, radio or select field"
    return mismatch(name, path, "\"${target.key}\" is a ${target.type} field, so \"$name\" $result matches it", hint)
}

private fun listComparisonIssue(
    target: FormField,
    name: String,
    path: String,
): List<DefinitionIssue> {
    val result = if (name == "neq" || name == "notIn") "always" else "never"
    return mismatch(
        name,
        path,
        "\"${target.key}\" holds a list of values, so \"$name\" $result matches it",
        "Use contains, notContains or notEmpty",
    )
}

private fun operandIssue(
    target: FormField,
    name: String,
    operand: JsonValue?,
    path: String,
): List<DefinitionIssue> {
    val operands: List<JsonValue?> = operand?.arrayValue?.takeIf { name in listOperators } ?: listOf(operand)
    val expected = expectedType(target)
    if (expected != null) {
        val wrong = operands.indexOfFirst { (it?.typeName ?: "undefined") != expected }
        if (wrong >= 0) {
            val value = operands[wrong]
            val problem = "${jsonStringify(
                value,
            )} is a ${value?.typeName ?: "undefined"}, but \"${target.key}\" holds a $expected"
            return mismatch(name, path, problem, "Use a $expected value")
        }
    }
    val options = target.options
    if (!(expected == "string" || target.type in listTypes) || target.hasOptionsFrom ||
        options.isNullOrEmpty()
    ) {
        return emptyList()
    }
    val values = options.mapNotNull { it.value }
    val missing = operands.indexOfFirst { item -> values.none { strictEquals(JsonValue.String(it), item) } }
    if (missing < 0) return emptyList()
    val code = if (name in containsOperators) IssueCode.CONTAINS_NOT_IN_OPTIONS else IssueCode.CONDITION_VALUE_MISMATCH
    return listOf(
        warning(code, path, "${jsonStringify(operands[missing])} is not an option of \"${target.key}\"", oneOf(values)),
    )
}

private fun conditionWarnings(
    context: CheckContext,
    entry: ConditionEntry,
): List<DefinitionIssue> {
    if (conditionErrors(context, entry).isNotEmpty()) return emptyList()
    val field = entry.condition.field ?: return emptyList()
    val name = entry.condition.operatorName ?: return emptyList()
    val target = context.target(field, entry.group) ?: return emptyList()
    return valueIssue(target.field, name, entry.condition.operand(name), "${entry.path}/$name")
}

private val choiceTypes = listOf("select", "radio", "multiselect")

private fun optionWarnings(entry: FieldEntry): List<DefinitionIssue> {
    val field = entry.field
    if (field.type !in choiceTypes) return emptyList()
    val options = field.options
    if (options == null) {
        if (field.hasOptionsFrom) return emptyList()
        val problem = "the ${field.type} field \"${field.key}\" has no options"
        return listOf(
            warning(IssueCode.MISSING_OPTIONS, "${entry.path}/options", problem, "Add \"options\" or \"optionsFrom\""),
        )
    }
    val values = options.mapNotNull { it.value }
    val defaultValue = field.defaultValue
    if (defaultValue == null || values.isEmpty() || field.hasOptionsFrom) return emptyList()
    val defaults =
        if (field.type ==
            "multiselect"
        ) {
            defaultValue.arrayValue ?: listOf(defaultValue)
        } else {
            listOf(defaultValue)
        }
    val missing =
        defaults.firstOrNull { item -> values.none { strictEquals(JsonValue.String(it), item) } } ?: return emptyList()
    val problem = "${jsonStringify(missing)} is not an option of \"${field.key}\""
    return listOf(warning(IssueCode.DEFAULT_NOT_IN_OPTIONS, "${entry.path}/defaultValue", problem, oneOf(values)))
}

private fun routeWarnings(context: CheckContext): List<DefinitionIssue> {
    val steps = context.definition.steps.orEmpty()
    return steps.withIndex().flatMap { (index, step) ->
        val issues = ArrayList<DefinitionIssue>()
        val targets =
            step.routes
                .orEmpty()
                .mapNotNull { it.to }
                .distinct()
        for (target in targets) {
            val position = steps.indexOfFirst { it.id == target }
            val sibling = steps.getOrNull(position + 1)
            val exits =
                position >= 0 &&
                    steps[position].routes.orEmpty().any { it.show.isNullOrEmpty() && it.showAny.isNullOrEmpty() }
            if (sibling == null || sibling.id !in targets || exits) continue
            val problem = "branch \"$target\" from \"${step.id}\" continues into the sibling branch \"${sibling.id}\""
            val hint = "Add an unconditional route on \"$target\" to the step where the branches meet"
            issues.add(warning(IssueCode.ROUTE_FALLTHROUGH, "/steps/$position", problem, hint))
        }
        if (step.canSkip && (step.next == StepButton.Hidden || steps.size < 2)) {
            val problem = "step \"${step.id}\" has \"skip\" but no Continue button or later step"
            issues.add(
                warning(
                    IssueCode.SKIP_UNREACHABLE,
                    "/steps/$index/skip",
                    problem,
                    "Remove \"skip\", or remove \"next\": false",
                ),
            )
        }
        issues
    }
}

private fun fieldWarnings(
    context: CheckContext,
    entry: FieldEntry,
): List<DefinitionIssue> =
    typeWarnings(entry) + optionWarnings(entry) + ruleWarnings(context, entry) + sourceWarnings(entry) +
        autocompleteWarnings(entry) +
        entry.field.optionsDependsOn.orEmpty().withIndex().flatMap { (index, key) ->
            missingTarget(context, key, "${entry.path}/optionsDependsOn/$index").map {
                it.withSeverity(DefinitionIssue.Severity.WARNING)
            }
        }

internal fun definitionWarnings(context: CheckContext): List<DefinitionIssue> =
    versionWarnings(
        context,
    ) + propertyWarnings(context) + context.conditions.flatMap { conditionWarnings(context, it) } +
        context.entries.flatMap { fieldWarnings(context, it) } + routeWarnings(context) + recallWarnings(context) +
        exclusiveWarnings(context)
