package dev.formhaus.compose.model

import dev.formhaus.core.engine.FormLifecycleError
import dev.formhaus.core.engine.GoToStepResult
import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.renderer.advancesOnCommit
import dev.formhaus.core.renderer.autoAdvance
import dev.formhaus.core.renderer.errorsVersion
import dev.formhaus.core.renderer.firstErrorKey
import dev.formhaus.core.renderer.nodeId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

public fun FormModel.setValue(
    key: String,
    value: JsonValue?,
) {
    if (disabled) {
        if (fieldStates[key]?.hasFocus == true) heldEdits[key] = value
        return
    }
    heldEdits.remove(key)
    dropFocusRequest()
    engine.setValue(key, value)
    options.onFieldChange?.invoke(key, value, engine.values)
}

public fun FormModel.commitValue(
    key: String,
    value: JsonValue?,
) {
    if (disabled) return
    val step = engine.currentStep?.nodeId
    val field = engine.visibleFields.firstOrNull { it.key == key }
    setValue(key, value)
    if (field?.advancesOnCommit != true || engine.currentStep?.nodeId != step || engine.isLastStep) return
    scope.launch { run { engine.autoAdvance() } }
}

internal inline fun FormModel.changeItems(
    group: String,
    apply: () -> Boolean,
): Boolean {
    if (disabled || !apply()) return false
    options.onFieldChange?.invoke(group, engine.values[group], engine.values)
    return true
}

public suspend fun FormModel.next() {
    run {
        val action = if (engine.isLastStep) null else engine.currentStep?.next?.action
        !engine.isActionDisabled(action) && engine.next()
    }
}

public suspend fun FormModel.back() {
    run { engine.back() }
}

public suspend fun FormModel.skip() {
    var sent = false
    run {
        engine
            .skip { values, skipped ->
                sent = true
                submitHandler(values, skipped)
            }.also { if (it && sent) snapshots.submitted() }
    }
}

public suspend fun FormModel.submit() {
    run { (!engine.isActionDisabled(engine.definition.submit) && engine.submit(submitHandler)).also { if (it) snapshots.submitted() } }
}

public suspend fun FormModel.performPrimaryAction() {
    if (actions.isLastStep) submit() else next()
}

public fun FormModel.cancel() {
    options.onCancel?.invoke()
}

public fun FormModel.setErrors(errors: FieldErrors) {
    engine.setErrors(errors)
    if (!errors.isEmpty()) requestFocus(engine.firstErrorKey(), afterErrors = true)
}

public fun FormModel.goToStep(stepId: String): GoToStepResult = engine.goToStep(stepId)

public fun FormModel.focus(field: String) {
    engine.goToStepWithField(field)
    requestFocus(field)
}

internal suspend fun FormModel.run(action: suspend () -> Boolean) {
    withContext(Dispatchers.Main.immediate) {
        if (loading) return@withContext
        alerts.reportActionError(null)
        val errors = engine.errorsVersion
        try {
            val done = action()
            if (!done && engine.errorsVersion != errors && !engine.errors.isEmpty()) noteInvalidAttempt()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            reportError(error)
        }
        applyHeldEdits()
    }
}

internal fun FormModel.reportError(error: Throwable) {
    options.onError?.invoke(error) ?: alerts.reportActionError(actionMessage(error))
}

internal fun FormModel.loadingChanged() {
    refreshDisabled()
    applyHeldEdits()
}

private fun FormModel.applyHeldEdits() {
    if (disabled || heldEdits.isEmpty()) return
    val edits = heldEdits.toMap()
    heldEdits.clear()
    for ((key, value) in edits) setValue(key, value)
}

private fun actionMessage(error: Throwable): String = (error as? FormLifecycleError)?.message ?: "Form action failed"
