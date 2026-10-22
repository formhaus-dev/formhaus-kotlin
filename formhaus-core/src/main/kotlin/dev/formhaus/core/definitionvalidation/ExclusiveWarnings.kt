package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.sameValueZero
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.exclusiveValues

private fun exclusiveIssues(field: FormField, path: String, exclusive: List<JsonValue>): List<DefinitionIssue> {
    val options = field.options.orEmpty()
    val first = options.indexOfFirst { it.exclusive }.let { if (it < 0) options.size else it }
    val issues = ArrayList<DefinitionIssue>()
    if (options.drop(first).any { !it.exclusive }) {
        issues.add(warning(IssueCode.EXCLUSIVE_OPTION_POSITION, "$path/options/$first", "exclusive options of \"${field.key}\" are not last"))
    }
    val defaults = field.defaultValue?.arrayValue.orEmpty()
    if (defaults.size > 1 && defaults.any { value -> exclusive.any { sameValueZero(it, value) } }) {
        val problem = "the default of \"${field.key}\" mixes exclusive and other options"
        issues.add(warning(IssueCode.EXCLUSIVE_DEFAULT_VALUE, "$path/defaultValue", problem))
    }
    if ((field.validation?.minLength ?: 0.0) > 1) {
        val problem = "\"minLength\" does not apply to an exclusive option of \"${field.key}\""
        issues.add(warning(IssueCode.EXCLUSIVE_WITH_MIN_LENGTH, "$path/validation/minLength", problem))
    }
    return issues
}

internal fun exclusiveWarnings(context: CheckContext): List<DefinitionIssue> = context.entries.flatMap { entry ->
    val field = entry.field
    val exclusive = exclusiveValues(field)
    if (exclusive.isNotEmpty()) return@flatMap exclusiveIssues(field, entry.path, exclusive)
    field.options.orEmpty().withIndex().filter { it.value.exclusive }.map { (index, _) ->
        warning(IssueCode.EXCLUSIVE_NOT_APPLICABLE, "${entry.path}/options/$index/exclusive", "\"${field.key}\" is not a multiselect with static options")
    }
}
