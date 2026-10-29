package dev.formhaus.core.engine

import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.FormNode
import dev.formhaus.core.model.valueFields
import dev.formhaus.core.regex.CallBudget
import dev.formhaus.core.validation.MessageSource
import dev.formhaus.core.validation.ShownChecks
import dev.formhaus.core.validation.fieldValueError
import dev.formhaus.core.validation.validateShown
import dev.formhaus.core.visibility.itemPath
import dev.formhaus.core.visibility.itemScope
import dev.formhaus.core.visibility.itemsOf

internal fun errorKeys(
    nodes: List<FormNode>,
    errors: FieldErrors,
    values: JsonObject,
): List<String> {
    if (errors.isEmpty()) return emptyList()
    return valueFields(nodes).flatMap { field ->
        val own = if (field.type != "hidden" && errors[field.key] != null) listOf(field.key) else emptyList()
        if (!field.isGroup) return@flatMap own
        val paths =
            itemsOf(values[field.key]).indices.flatMap { index ->
                field.items.map { itemPath(field.key, index, it.key) }
            }
        own + paths.filter { errors[it] != null }
    }
}

private inline fun <T> EngineCore.unsettleOnWrite(run: () -> T): T {
    val before = version
    val result = run()
    if (before != version) visibility.unsettle()
    return result
}

internal val EngineCore.messageSource: MessageSource get() = MessageSource.Function(messages)

internal fun EngineCore.fieldsErrors(
    nodes: List<FormNode>,
    submitValues: JsonObject? = null,
): FieldErrors {
    val values = submitValues ?: getSubmitValues()
    val scope = visibility.conditionValues(this.values.json)
    val checks = ShownChecks(validators, messageSource, visibility.limits(this.values.json))
    return CallBudget.run { unsettleOnWrite { validateShown(nodes, values, scope, checks) } }
}

internal fun EngineCore.fieldError(
    field: FormField,
    value: JsonValue?,
    allValues: JsonObject? = null,
): String? {
    val all = allValues ?: getSubmitValues()
    return CallBudget.run { unsettleOnWrite { fieldValueError(field, value, all, validators, messageSource) } }
}

internal fun EngineCore.itemError(item: ItemField): String? {
    val values = getSubmitValues()
    val items = itemsOf(values[item.group.key])
    val scope = itemScope(item.group, items.getOrNull(item.index) ?: JsonValue.Null, values)
    return fieldError(item.field, scope[item.field.key], scope)
}

internal fun EngineCore.applyValidationErrors(
    found: FieldErrors,
    server: Boolean = false,
    formErrors: List<String> = emptyList(),
    navigate: Boolean = server,
) {
    val bound = MutableStringMap<String>()
    val general = formErrors.toMutableList()
    for ((key, message) in found) {
        if (visibility.activeField(key, values.json) != null ||
            visibility.activeItem(key, values.json) != null
        ) {
            bound[key] = message
        } else {
            general.add(message)
        }
    }
    if (server) {
        replaceServerErrors(bound)
        serverFormErrors = general
    }
    replaceErrors(serverErrors.merging(bound))
    topLevelErrors = if (server) general else serverFormErrors + general
    notify(structureChanged = navigate && moveToFirstError(bound))
}

internal fun EngineCore.moveToFirstError(found: FieldErrors): Boolean {
    val target = visibleSteps.indexOfFirst { errorKeys(it.fields, found, values.json).isNotEmpty() }
    if (target < 0 || target == currentStepIndex) return false
    currentStepIndex = target
    return true
}

internal fun EngineCore.clearError(
    key: String,
    server: Boolean = false,
) {
    if (server) serverErrors.remove(key)
    errors.assign(key, serverErrors[key])
}

internal fun EngineCore.clearPassedErrors(nodes: List<FormNode>) {
    val hidden = valueFields(nodes).filter { it.type == "hidden" && errors[it.key] != null }.map { it.key }
    for (key in errorKeys(nodes, errors, values.json) + hidden) clearError(key)
}

internal fun EngineCore.dropServerErrors(keys: Iterable<String>) {
    val dropped = keys.toHashSet()
    for (key in serverErrors.keys) if (key in dropped || visibility.owner(key) in dropped) serverErrors.remove(key)
}

internal fun EngineCore.clearResolvedMatchErrors(
    key: String,
    item: ItemField? = null,
): List<String> {
    if (item != null) {
        return clearResolvedMatches(key, item.group.items.map { itemPath(item.group.key, item.index, it.key) to it }) {
            itemError(ItemField(item.group, item.index, it))
        }
    }
    val fieldCleared = clearResolvedMatches(key, visibility.allFields.map { it.key to it }) { fieldError(it, values[it.key]) }
    return fieldCleared + visibility.groups.flatMap { clearResolvedGroupMatches(key, it) }
}

private fun EngineCore.clearResolvedMatches(
    key: String,
    fields: List<Pair<String, FormField>>,
    errorOf: (FormField) -> String?,
): List<String> {
    val cleared = ArrayList<String>()
    for ((path, field) in fields) {
        if (field.validation?.matchField != key || errors[path] == null || errorOf(field) != null) continue
        clearError(path)
        cleared.add(path)
    }
    return cleared
}

private fun EngineCore.clearResolvedGroupMatches(
    key: String,
    group: FormField,
): List<String> {
    if (visibility.activeField(group.key, values.json)?.ordinal != group.ordinal) return emptyList()
    if (group.items.none { it.validation?.matchField == key }) return emptyList()
    val first = group.items.firstOrNull() ?: return emptyList()
    return itemsOf(values[group.key]).indices.flatMap { clearResolvedMatchErrors(key, ItemField(group, it, first)) }
}

internal fun EngineCore.pruneOffPathErrors(): List<String> {
    val pathFields = visibleSteps.flatMap { step -> valueFields(step.fields).map { it.key } }.toHashSet()
    val pruned = errors.keys.filter { visibility.owner(it) !in pathFields }
    for (key in pruned) errors.remove(key)
    dropServerErrors(pruned)
    return pruned
}

internal fun EngineCore.getSubmitValues(payload: Boolean = false): JsonObject {
    val result: MutableJsonObject = visibility.conditionValues(values.json).toMutableStringMap()
    if (payload && visibility.keep) addKept(result)
    for (entry in skipped) for (key in entry.keys) result.remove(key)
    return result
}

internal fun EngineCore.validate(navigate: Boolean = false): FieldErrors {
    val found = CallBudget.run(::collectErrors)
    replaceErrors(serverErrors.merging(found))
    notify(structureChanged = navigate && moveToFirstError(found))
    return found
}

private fun EngineCore.collectErrors(): FieldErrors {
    if (!isMultiStep) return fieldsErrors(definition.fields.orEmpty())
    val found = MutableStringMap<String>()
    var seen = -1
    var submitValues: JsonObject = MutableJsonObject()
    for (step in visibleSteps) {
        if (isStepSkipped(step.id)) continue
        if (seen != version) {
            seen = version
            submitValues = getSubmitValues()
        }
        found.merge(fieldsErrors(step.fields, submitValues))
    }
    return found
}

internal fun EngineCore.validateOne(key: String): String? {
    val field = visibility.activeField(key, values.json)
    val item = if (field == null) visibility.activeItem(key, values.json) else null
    if (field == null && item == null) return null
    val error = if (item != null) itemError(item) else field?.let { fieldError(it, values[key]) }
    if (error != null) errors[key] = error else clearError(key, server = true)
    notify()
    return error
}
