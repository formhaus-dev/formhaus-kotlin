package dev.formhaus.core.engine

import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.model.FormStep
import dev.formhaus.core.regex.CallBudget
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException

internal class StepMove(val step: FormStep, val target: FormStep, val index: Int, val forward: Boolean, val skipping: Boolean) {
    val validating: Boolean get() = forward && !skipping
}

internal fun EngineCore.plannedMove(forward: Boolean, reason: StepChangeReason): StepMove? {
    val skipping = reason == StepChangeReason.SKIP
    if (!isMultiStep || stepValidating || submitting) return null
    if (!skipping && (if (forward) isLastStep else isFirstStep)) return null
    val step = currentStep ?: return null
    val validating = forward && !skipping
    if (validating) includeCurrentStep()
    if (validating && hasStepErrors()) return null
    val index = currentStepIndex + if (forward) 1 else -1
    val target = (if (skipping) skipTarget() else visibleSteps.getOrNull(index)) ?: return null
    return StepMove(step, target, index, forward, skipping)
}

internal fun EngineCore.beginOperation(): Int {
    operationEpoch += 1
    latestOperation = operationEpoch
    return operationEpoch
}

private fun EngineCore.activePathKey(): List<String> = visibleSteps.map { it.id }

internal fun EngineCore.watchCheckedInputs(checked: JsonObject): () -> Boolean {
    val path = activePathKey()
    val seen = checked.keys.map { it to values.slot(it) }
    return {
        activePathKey() != path || seen.any { (key, slot) -> !values.slot(key).isSame(slot) } ||
            getSubmitValues().keys.any { key -> key !in checked && visibility.activeField(key, values.json)?.type != "hidden" }
    }
}

internal fun EngineCore.hasStepErrors(): Boolean {
    val step = currentStep ?: return true
    val found = fieldsErrors(step.fields)
    if (found.isEmpty()) return false
    replaceErrors(errors.merging(found))
    notify()
    return true
}

private suspend fun settle() {
    withContext(NonCancellable) { yield() }
}

internal suspend fun <T> suspended(body: suspend () -> T): T {
    val result = try {
        body()
    } catch (error: Throwable) {
        settle()
        throw error
    }
    settle()
    return result
}

internal suspend fun FormEngine.reportErrors(action: suspend () -> Boolean): Boolean {
    val generation = core.errorsGeneration
    val done = CallBudget.runSuspending { suspended(action) }
    if (!done && core.errorsGeneration != generation) {
        for ((fieldKey, error) in core.errors) core.emit(FormEvent.FieldError(fieldKey, error))
    }
    return done
}

internal suspend fun afterCommit(phase: FormLifecycleError.Phase, callback: suspend () -> Unit) {
    try {
        suspended(callback)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        throw FormLifecycleError(phase, error)
    }
}

internal suspend fun FormEngine.advance(reason: StepChangeReason): Boolean = reportErrors { changeStep(true, reason) }

internal suspend fun FormEngine.changeStep(forward: Boolean, reason: StepChangeReason): Boolean {
    val core = core
    val move = core.plannedMove(forward, reason) ?: return false
    val step = move.step
    var committed = false
    val context = StepChangeContext(step.id, move.target.id, reason, core.getSubmitValues(payload = true))
    val epoch = core.validationEpoch
    val operation = core.beginOperation()
    val inputsChanged = core.watchCheckedInputs(context.values)
    val stale = {
        epoch != core.validationEpoch || operation != core.operationEpoch || core.currentStep?.id != step.id || inputsChanged()
    }
    core.stepValidating = true
    core.notify()
    try {
        if (!approve(move, context, stale)) return false
        committed = true
        commit(move)
        suspended { afterCommit(FormLifecycleError.Phase.AFTER_STEP_CHANGE) { options.onAfterStepChange?.invoke(context) } }
        return true
    } catch (error: CancellationException) {
        return committed
    } catch (error: Throwable) {
        if (!committed && stale()) return false
        throw error
    } finally {
        if (operation == core.operationEpoch) {
            core.stepValidating = false
            core.notify()
        }
    }
}

private suspend fun FormEngine.approve(move: StepMove, context: StepChangeContext, stale: () -> Boolean): Boolean {
    if (stale()) return false
    val validate = options.onStepValidate
    if (move.validating && validate != null) {
        val result = suspended { validate(move.step.id, context.values) }
        if (stale()) return false
        if (result != null && !result.isEmpty()) {
            core.applyValidationErrors(result)
            return false
        }
    }
    val before = options.onBeforeStepChange
    if (before != null) {
        val allowed = suspended { before(context) }
        if (stale() || allowed == false) return false
    }
    if (stale()) return false
    return !(move.validating && core.hasStepErrors())
}

private fun FormEngine.commit(move: StepMove) {
    if (move.forward) core.emit(if (move.skipping) FormEvent.StepSkipped(move.step.id) else FormEvent.StepCompleted(move.step.id))
    if (move.skipping) core.skipCurrentStep()
    moveTo(move.index)
}
