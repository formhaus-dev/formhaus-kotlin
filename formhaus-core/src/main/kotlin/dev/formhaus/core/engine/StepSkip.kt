package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.model.valueFields

private fun EngineCore.resetStepValues(): Values {
    val fields = valueFields(currentStep?.fields.orEmpty())
    val next = values.copy()
    for (field in fields) next.remove(field.key)
    next.merge(createValues(fields))
    return next
}

internal fun EngineCore.skipTarget(): FormStep? = activeSteps(definition, resetStepValues().json).getOrNull(currentStepIndex + 1)

internal fun EngineCore.skipCurrentStep(): Restore? = currentStep?.let(::skip)

internal fun EngineCore.skip(step: FormStep): Restore {
    val fieldKeys = valueFields(step.fields).map { it.key }
    baselineFields()
    replaceValues(resetStepValues())
    val restore = visibility.starts.reseed(fieldKeys, values)
    visibility.unsettle()
    for (key in errors.keys) if (visibility.owner(key) in fieldKeys) errors.remove(key)
    dropServerErrors(fieldKeys)
    markSkipped(step.id, fieldKeys)
    dropServerErrors(reconcileHidden())
    visibility.markChanged(structureChanged = true, valuesChanged = true)
    pruneOffPathErrors()
    notify(structureChanged = true, valuesChanged = true)
    return restore
}

internal fun EngineCore.includeCurrentStep() {
    currentStep?.let { unmarkSkipped(it.id) }
}

internal fun EngineCore.skipStep(index: Int) {
    currentStepIndex = index
    notify(structureChanged = true)
    skipCurrentStep()
}

internal fun EngineCore.reset(initialValues: JsonObject?) {
    val previousValues = values
    restarts += 1
    baselineFields()
    validationEpoch += 1
    operationEpoch += 1
    val visibility = visibility
    replaceValues(
        createValues(
            visibility.allFields,
            initialValues?.let(::Values),
        ) { key, json -> visibility.activeField(key, json) },
    )
    replaceErrors(MutableStringMap())
    replaceServerErrors(MutableStringMap())
    topLevelErrors = emptyList()
    serverFormErrors = emptyList()
    currentStepIndex = 0
    fieldLoading = MutableStringMap()
    stepValidating = false
    submitting = false
    skipped.clear()
    reconcileHidden(initial = true)
    if (started) {
        emit(FormEvent.FormReset)
        viewedStep = null
    }
    notify(structureChanged = true, valuesChanged = previousValues.changedKeys(values).isNotEmpty())
}
