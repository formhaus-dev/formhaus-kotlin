package dev.formhaus.core.definitionvalidation

import dev.formhaus.core.model.ShowCondition
import dev.formhaus.core.model.conditionOperators

private fun operatorIssues(condition: ShowCondition, path: String): List<DefinitionIssue> {
    val keys = condition.json.keys.filter { it != "field" }
    val unknown = keys.filter { it !in conditionOperators }
    val issues = unknown.mapTo(ArrayList()) {
        error(IssueCode.UNKNOWN_OPERATOR, "$path/${pointer(it)}", "\"$it\" is not a condition operator", suggest(it, conditionOperators))
    }
    val count = keys.size - unknown.size
    if (count != 1 && !(count == 0 && unknown.isNotEmpty())) {
        val problem = if (count > 0) "the condition has $count operators" else "the condition has no operator"
        issues.add(error(IssueCode.CONDITION_OPERATOR_COUNT, path, problem, "Use exactly one of: ${conditionOperators.joinToString(", ")}"))
    }
    return issues
}

internal fun missingTarget(context: CheckContext, name: String, path: String, group: String? = null, key: String? = null): List<DefinitionIssue> {
    if (context.target(name, group) != null) return emptyList()
    val inner = context.scopeOrder.firstOrNull { context.scopes[it]?.get(name) != null }
    if (inner != null) {
        return listOf(error(IssueCode.GROUP_SCOPE, path, "\"$name\" is a field inside the group \"$inner\"", "Only fields of the same group can use it"))
    }
    val scoped = group?.let { context.scopes[it]?.keys }.orEmpty()
    val candidates = (scoped + context.keyList).filter { key == null || it != key }
    return listOf(error(IssueCode.UNKNOWN_FIELD, path, "\"$name\" is not a field key", suggest(name, candidates)))
}

private fun referenceIssues(context: CheckContext, entry: ConditionEntry, field: String): List<DefinitionIssue> {
    val target = context.target(field, entry.group) ?: return missingTarget(context, field, "${entry.path}/field", entry.group, entry.key)
    if (target.group != null || entry.step < 0 || entry.owner == ConditionOwner.ACTION) return emptyList()
    val isStep = entry.owner == ConditionOwner.STEP
    if (target.step < entry.step || (!isStep && target.step == entry.step)) return emptyList()
    val sameStep = if (isStep) "this or a later step" else "a later step"
    val allowed = if (isStep) "earlier steps" else "this or an earlier step"
    val owner = when (entry.owner) {
        ConditionOwner.ROUTE -> "Route"
        ConditionOwner.STEP -> "Step"
        else -> "Field"
    }
    return listOf(error(IssueCode.FORWARD_REFERENCE, "${entry.path}/field", "\"$field\" belongs to $sameStep", "$owner conditions can use fields of $allowed"))
}

internal fun conditionErrors(context: CheckContext, entry: ConditionEntry): List<DefinitionIssue> {
    val field = entry.condition.field
        ?: return listOf(error(IssueCode.MISSING_PROPERTY, entry.path, "missing required property \"field\"", "Add the key of the field to test"))
    return operatorIssues(entry.condition, entry.path) + referenceIssues(context, entry, field)
}

internal fun conditionIssues(context: CheckContext): List<DefinitionIssue> = context.conditions.flatMap { conditionErrors(context, it) }
