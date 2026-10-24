package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.validation.booleanTypes
import dev.formhaus.core.validation.builtInTypes
import dev.formhaus.core.validation.numberTypes

internal fun ruleWarnings(
    context: CheckContext,
    entry: FieldEntry,
): List<DefinitionIssue> {
    val rules = entry.field.validation?.json
    return inapplicableRules(entry, rules) + itemLimitWarnings(entry) + slowPatternWarning(entry, rules) +
        unknownValidator(context, entry, rules)
}

private fun slowPatternWarning(
    entry: FieldEntry,
    rules: JsonObject?,
): List<DefinitionIssue> {
    val slow = rules?.get("pattern")?.stringValue?.let(::slowPattern) ?: return emptyList()
    val (behavior, hint) =
        when (slow) {
            SlowPattern.NESTED -> "nests quantifiers" to "Remove the inner or outer quantifier"
            SlowPattern.ADJACENT -> "repeats overlapping parts next to each other" to "Merge the repeated parts into one"
            SlowPattern.LONG -> "is longer than 1000 characters" to "Shorten it to 1000 characters or less"
        }
    val problem = "the pattern of \"${entry.field.key}\" $behavior and can be very slow"
    return listOf(warning(IssueCode.SLOW_PATTERN, "${entry.path}/validation/pattern", problem, hint))
}

private fun inapplicableRules(
    entry: FieldEntry,
    rules: JsonObject?,
): List<DefinitionIssue> {
    val field = entry.field

    fun rule(
        name: String,
        problem: String,
        hint: String,
    ) = warning(IssueCode.RULE_NOT_APPLICABLE, "${entry.path}/validation/$name", problem, hint)

    fun present(vararg names: String) = names.filter { rules?.contains(it) == true }
    val items = field.isGroup
    val known = items || field.type in builtInTypes
    val numeric = field.type in numberTypes
    val ranges = if (known && !numeric) present("min", "max") else emptyList()
    val sized = if (known && field.type in numberTypes + booleanTypes) present("minLength", "maxLength") else emptyList()
    val rangeIssues =
        ranges.map { name ->
            rule(
                name,
                "\"$name\" applies to number fields only",
                "Use ${name}Length to limit ${if (items) "the number of items" else "text length"}",
            )
        }
    val sizedIssues =
        sized.map { name ->
            rule(
                name,
                "\"$name\" does not apply to ${field.type} fields",
                if (numeric) "Use min and max for numbers" else "Remove $name",
            )
        }
    return rangeIssues + sizedIssues
}

private fun unknownValidator(
    context: CheckContext,
    entry: FieldEntry,
    rules: JsonObject?,
): List<DefinitionIssue> {
    val validator = rules?.get("validator")?.stringValue
    val validators = context.validators
    if (validator == null || validators == null || validator in validators) return emptyList()
    return listOf(
        warning(
            IssueCode.UNKNOWN_VALIDATOR,
            "${entry.path}/validation/validator",
            "the validator \"$validator\" is not registered",
            "Pass it in the \"validators\" option",
        ),
    )
}

private val customTypeSeparators = setOf('-', '_', ':')

internal fun typeWarnings(entry: FieldEntry): List<DefinitionIssue> {
    val field = entry.field
    val custom = field.type !in builtInTypes && field.type != "group"
    val named = '-' in field.type || ':' in field.type
    val spelled = field.type.filterNot { it in customTypeSeparators }
    val match =
        when {
            !custom -> null
            named -> builtInTypes.firstOrNull { it == spelled }
            else -> closest(field.type, builtInTypes + listOf("group", "section"))
        }
    val path = "${entry.path}/type"
    val problem = "\"${field.type}\" is not a built-in field type"
    return when {
        match != null -> {
            listOf(
                warning(
                    IssueCode.UNKNOWN_FIELD_TYPE,
                    path,
                    problem,
                    "Use \"$match\", or register a component for \"${field.type}\"",
                ),
            )
        }

        custom && !named -> {
            val hint = "Name custom types with a dash or colon, like \"x-rating\", so a future built-in type cannot take the name"
            listOf(warning(IssueCode.CUSTOM_TYPE_NAME, path, problem, hint))
        }

        else -> {
            emptyList()
        }
    }
}
