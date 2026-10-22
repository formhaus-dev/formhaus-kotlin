package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableJsonObject
import dev.formhaus.core.model.FormDefinition
import dev.formhaus.core.model.FormField
import dev.formhaus.core.model.valueFields

private fun kept(
    fields: List<FormField>?,
    field: FormField?,
): Boolean = field != null && fields?.any { it.key == field.key && it.type == field.type } == true

private fun sameItems(
    previous: JsonValue?,
    next: JsonValue?,
): Boolean {
    val old = previous?.arrayValue ?: return false
    val new = next?.arrayValue ?: return false
    return old == new
}

private fun carriedValues(
    values: Values,
    fields: List<FormField>,
    previous: VisibilityState,
): Values {
    val result = Values()
    for (field in fields) {
        if (!kept(previous.allFields, field) || !values.has(field.key)) continue
        val slot = values.slot(field.key)
        val items = slot.value?.arrayValue
        if (!field.isGroup || items == null) {
            result.set(field.key, slot)
            continue
        }
        val old = previous.groups.firstOrNull { it.key == field.key }?.items
        val filtered =
            items.map { item ->
                val entry = item.objectValue ?: return@map item
                val next = MutableJsonObject()
                for ((key, value) in entry) if (kept(old, field.items.firstOrNull { it.key == key })) next[key] = value
                JsonValue.Object(next)
            }
        result.set(field.key, ValueSlot(JsonValue.Array(filtered), Ref(freshToken(), values.itemIds(field.key))))
    }
    return result
}

private fun EngineCore.keepsError(
    key: String,
    previous: VisibilityState,
): Boolean {
    val parsed = visibility.parse(key)
    val field = visibility.activeField(parsed?.group ?: key, values.json)
    if (!kept(previous.allFields, field)) return false
    if (parsed == null) return true
    val old = previous.groups.firstOrNull { it.key == parsed.group }?.items
    return kept(old, field?.items?.firstOrNull { it.key == parsed.field })
}

internal fun EngineCore.replaceDefinition(next: FormDefinition) {
    val previous = visibility
    val previousValues = keptValues().copy()
    val steps = visibleSteps
    val stepIndex = currentStepIndex
    baselineFields()
    load(next, carriedValues(previousValues, next.allFields, previous))
    validationEpoch += 1
    operationEpoch += 1
    stepValidating = false
    if (sending != latestOperation) submitting = false
    for (key in errors.keys) if (!keepsError(key, previous)) errors.remove(key)
    for (key in serverErrors.keys) if (errors[key] == null) serverErrors.remove(key)
    currentStepIndex = reconcileStepIndex(steps, visibleSteps, stepIndex)
    val nextSteps = next.steps
    val carried =
        skipped.mapNotNull { entry ->
            nextSteps?.firstOrNull { it.id == entry.id }?.let { step ->
                SkippedKeys(entry.id, valueFields(step.fields).map { it.key })
            }
        }
    skipped.clear()
    skipped.addAll(carried)
    val current = keptValues()
    val valuesChanged = previousValues.changedKeys(current).any { !sameItems(previousValues[it], current[it]) }
    notify(structureChanged = true, valuesChanged = valuesChanged)
    if (!valuesChanged) trackChanges(true)
}
