package dev.formhaus.core.engine

import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.model.FormStep

private class SkippedStep(
    val step: FormStep,
    val values: Values,
    val errors: MutableStringMap<String>,
    val serverErrors: MutableStringMap<String>,
    val restore: Restore,
)

internal suspend fun FormEngine.skipStep(submit: SubmitHandler?): Boolean {
    val core = core
    if (!core.isMultiStep || core.stepValidating || core.submitting || core.currentStep?.canSkip != true) return false
    if (core.skipTarget() != null) return changeStep(true, StepChangeReason.SKIP)
    val step = core.currentStep
    if (submit == null || step == null) return false
    val values = core.values.copy()
    val errors = core.errors.toMutableStringMap()
    val serverErrors = core.serverErrors.toMutableStringMap()
    val outcome = Box<Boolean?>(null)
    val skipped = SkippedStep(step, values, errors, serverErrors, core.skip(step))
    val skippedGeneration = core.valuesGeneration
    val skippedValues = core.values.copy()
    val watch = core.visibility.starts.watchWrites()
    val operationBox = Box(core.latestOperation)
    try {
        return suspended {
            submitForm(
                { submitted, skippedSteps ->
                    val result = suspended { submit(submitted, skippedSteps) }
                    val rejected = result.isRejection
                    if (!rejected || operationBox.value == core.operationEpoch) outcome.value = rejected
                    result
                },
                keepSkipped = true,
                operationBox = operationBox,
            )
        }
    } finally {
        val writes = core.visibility.starts.endWatch(watch)
        if (outcome.value != false && operationBox.value == core.latestOperation &&
            core.valuesGeneration == skippedGeneration
        ) {
            restoreSkipped(
                skipped,
                outcome.value,
                LinkedHashSet(skippedValues.changedKeys(core.values)) + writes,
                writes,
            )
        }
    }
}

private fun FormEngine.restoreSkipped(
    saved: SkippedStep,
    outcome: Boolean?,
    kept: Set<String>,
    writes: Set<String>,
) {
    val core = core
    val visibility = core.visibility
    val errors = saved.errors
    val serverErrors = saved.serverErrors
    val steps = core.visibleSteps
    val current = core.values.copy()
    core.baselineFields()
    val restored = saved.values.copy()
    for (key in kept) {
        restored.remove(key)
        for (errorKey in errors.keys) if (visibility.owner(errorKey) == key) errors.remove(errorKey)
        for (errorKey in serverErrors.keys) if (visibility.owner(errorKey) == key) serverErrors.remove(errorKey)
        if (current.has(key)) restored.set(key, current.slot(key))
    }
    core.replaceValues(restored)
    val reverted = current.changedKeys(core.values)
    if (outcome == null) core.replaceServerErrors(serverErrors) else core.dropServerErrors(reverted)
    val fresh = core.errors.toMutableStringMap()
    for (key in fresh.keys) if (visibility.owner(key) in reverted) fresh.remove(key)
    core.replaceErrors((if (outcome == true) MutableStringMap() else errors).merging(fresh))
    core.unmarkSkipped(saved.step.id)
    visibility.starts.restore(saved.restore, writes)
    visibility.unsettle()
    core.dropServerErrors(core.reconcileHidden())
    visibility.markChanged(structureChanged = true, valuesChanged = true)
    core.currentStepIndex = reconcileStepIndex(steps, core.visibleSteps, core.currentStepIndex)
    core.pruneOffPathErrors()
    core.notify(structureChanged = true, valuesChanged = true)
}
