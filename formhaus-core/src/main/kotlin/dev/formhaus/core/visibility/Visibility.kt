package dev.formhaus.core.visibility

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.isBlankText
import dev.formhaus.core.json.jsNumber
import dev.formhaus.core.json.sameValueZero
import dev.formhaus.core.json.strictEquals
import dev.formhaus.core.model.Conditional
import dev.formhaus.core.model.FormAction
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.FormSection
import dev.formhaus.core.model.ShowCondition
import dev.formhaus.core.model.conditionOperators
import dev.formhaus.core.validation.trimValue

internal fun isBlank(value: JsonValue?): Boolean = when (value) {
    null, JsonValue.Null -> true
    is JsonValue.String -> value.value.isEmpty()
    is JsonValue.Array -> value.items.isEmpty()
    else -> false
}

private fun toNumber(value: JsonValue?): Double? {
    val number = when (value) {
        is JsonValue.String -> if (value.value.isBlankText) null else jsNumber(value.value)
        is JsonValue.Number -> value.value
        else -> null
    }
    return number?.takeIf { it.isFinite() }
}

private inline fun compare(value: JsonValue?, operand: JsonValue?, test: (Double, Double) -> Boolean): Boolean {
    val left = toNumber(value) ?: return false
    val right = operand?.numberValue ?: return false
    return test(left, right)
}

public fun evaluateCondition(condition: ShowCondition, values: JsonObject): Boolean =
    evaluateCondition(condition, values, ListLimits.STANDARD)

internal fun evaluateCondition(condition: ShowCondition, values: JsonObject, limits: ListLimits): Boolean {
    val field = condition.field ?: return false
    val name = conditionOperators.firstOrNull { condition.operand(it) != null } ?: return false
    val value = values[field]
    val operand = condition.operand(name)
    val list = operand?.arrayValue.orEmpty()
    return when (name) {
        "eq" -> strictEquals(value, operand)
        "neq" -> !strictEquals(value, operand)
        "in" -> list.any { sameValueZero(it, value) }
        "notIn" -> list.none { sameValueZero(it, value) }
        "contains" -> limits.contains(field, value, operand)
        "notContains" -> !limits.contains(field, value, operand)
        "notEmpty" -> operand?.isTruthy != true || !isBlank(value)
        "gt" -> compare(value, operand) { left, right -> left > right }
        "gte" -> compare(value, operand) { left, right -> left >= right }
        "lt" -> compare(value, operand) { left, right -> left < right }
        else -> compare(value, operand) { left, right -> left <= right }
    }
}

public fun isVisible(node: Conditional, values: JsonObject): Boolean = isVisible(node, values, ListLimits.STANDARD)

internal fun isVisible(node: Conditional, values: JsonObject, limits: ListLimits): Boolean {
    val any = node.showAny.orEmpty()
    return node.show.orEmpty().all { evaluateCondition(it, values, limits) } &&
        (any.isEmpty() || any.any { evaluateCondition(it, values, limits) })
}

public fun isActionDisabled(action: FormAction?, values: JsonObject): Boolean = isActionDisabled(action, values, ListLimits.STANDARD)

internal fun isActionDisabled(action: FormAction?, values: JsonObject, limits: ListLimits): Boolean {
    val disabled = action?.disabled?.takeIf { it.isNotEmpty() } ?: return false
    return disabled.all { evaluateCondition(it, values, limits) }
}

internal fun shownFields(nodes: List<FormNode>, values: JsonObject, limits: ListLimits): List<FormField> = nodes.flatMap { node ->
    when {
        !isVisible(node, values, limits) -> emptyList()
        node is FormField -> listOf(node)
        else -> (node as FormSection).fields.filterIsInstance<FormField>().filter { isVisible(it, values, limits) }
    }
}

internal fun itemsOf(value: JsonValue?): List<JsonValue> = value?.arrayValue.orEmpty()

internal fun itemScope(group: FormField, item: JsonValue, values: JsonObject): JsonObject = itemScope(group.items, item, values)

internal fun itemScope(fields: List<FormField>, item: JsonValue, values: JsonObject): JsonObject {
    val scope = values.toMutableStringMap()
    val source = item.objectValue
    for (field in fields) scope.assign(field.key, trimValue(field, source?.get(field.key)))
    return scope
}

internal fun cleanItems(group: FormField, value: JsonValue?, values: JsonObject, limits: ListLimits, all: Boolean = false): JsonValue? {
    val items = value?.arrayValue ?: return value
    return JsonValue.Array(items.map { cleanItem(group.items, it, values, limits, all) })
}

internal fun cleanItem(fields: List<FormField>, item: JsonValue, values: JsonObject, limits: ListLimits, all: Boolean = false): JsonValue {
    if (item.objectValue == null) return item
    val scope = itemScope(fields, item, values)
    val result = MutableJsonObject()
    for (field in fields) {
        val value = scope[field.key] ?: continue
        if (all || isVisible(field, scope, limits)) result[field.key] = value
    }
    return JsonValue.Object(result)
}

internal fun itemPath(group: String, index: Int, field: String): String = "$group.$index.$field"

internal fun splitPath(group: String, key: String): Pair<Int, String>? {
    if (!key.startsWith("$group.")) return null
    val rest = key.substring(group.length + 1)
    val dot = rest.indexOf('.')
    if (dot <= 0) return null
    val digits = rest.substring(0, dot)
    val field = rest.substring(dot + 1)
    if (field.isEmpty() || !digits.all { it in '0'..'9' } || (digits.length > 1 && digits[0] == '0')) return null
    val index = digits.toIntOrNull() ?: return null
    return index to field
}
