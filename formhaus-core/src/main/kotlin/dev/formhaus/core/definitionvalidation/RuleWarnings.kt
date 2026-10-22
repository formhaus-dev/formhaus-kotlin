package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.validation.booleanTypes
import dev.formhaus.core.validation.builtInTypes
import dev.formhaus.core.validation.numberTypes

internal fun ruleWarnings(context: CheckContext, entry: FieldEntry): List<DefinitionIssue> {
    val field = entry.field
    val rules = field.validation?.json
    fun rule(name: String, problem: String, hint: String) = warning(IssueCode.RULE_NOT_APPLICABLE, "${entry.path}/validation/$name", problem, hint)
    val items = field.isGroup
    val known = items || field.type in builtInTypes
    val numeric = field.type in numberTypes
    val ranges = if (known && !numeric) listOf("min", "max").filter { rules?.contains(it) == true } else emptyList()
    val sized = if (known && field.type in numberTypes + booleanTypes) listOf("minLength", "maxLength").filter { rules?.contains(it) == true } else emptyList()
    val issues = ArrayList<DefinitionIssue>()
    for (name in ranges) {
        issues.add(rule(name, "\"$name\" applies to number fields only", "Use ${name}Length to limit ${if (items) "the number of items" else "text length"}"))
    }
    for (name in sized) {
        issues.add(rule(name, "\"$name\" does not apply to ${field.type} fields", if (numeric) "Use min and max for numbers" else "Remove $name"))
    }
    issues += itemLimitWarnings(entry)
    val pattern = rules?.get("pattern")?.stringValue
    val slow = pattern?.let(::slowPattern)
    if (slow != null) {
        val nested = slow == SlowPattern.NESTED
        val problem = "the pattern of \"${field.key}\" ${if (nested) "nests quantifiers" else "repeats overlapping parts next to each other"} and can be very slow"
        val hint = if (nested) "Remove the inner or outer quantifier" else "Merge the repeated parts into one"
        issues.add(warning(IssueCode.SLOW_PATTERN, "${entry.path}/validation/pattern", problem, hint))
    }
    val validator = rules?.get("validator")?.stringValue
    val validators = context.validators
    if (validator != null && validators != null && validator !in validators) {
        val problem = "the validator \"$validator\" is not registered"
        issues.add(warning(IssueCode.UNKNOWN_VALIDATOR, "${entry.path}/validation/validator", problem, "Pass it in the \"validators\" option"))
    }
    return issues
}

private val customTypeSeparators = setOf('-', '_', ':')

internal fun typeWarnings(entry: FieldEntry): List<DefinitionIssue> {
    val field = entry.field
    val custom = field.type !in builtInTypes && field.type != "group"
    val named = '-' in field.type || ':' in field.type
    val spelled = field.type.filterNot { it in customTypeSeparators }
    val match = when {
        !custom -> null
        named -> builtInTypes.firstOrNull { it == spelled }
        else -> closest(field.type, builtInTypes + listOf("group", "section"))
    }
    val path = "${entry.path}/type"
    val problem = "\"${field.type}\" is not a built-in field type"
    return when {
        match != null -> listOf(warning(IssueCode.UNKNOWN_FIELD_TYPE, path, problem, "Use \"$match\", or register a component for \"${field.type}\""))
        custom && !named -> {
            val hint = "Name custom types with a dash or colon, like \"x-rating\", so a future built-in type cannot take the name"
            listOf(warning(IssueCode.CUSTOM_TYPE_NAME, path, problem, hint))
        }
        else -> emptyList()
    }
}
