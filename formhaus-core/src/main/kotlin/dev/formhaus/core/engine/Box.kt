package dev.formhaus.core.engine

import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.MutableStringMap
import kotlin.coroutines.cancellation.CancellationException

internal class Box<V>(var value: V)

internal val SubmitResult?.isRejection: Boolean get() = this is SubmitResult.Rejected

private fun FormEngine.applyRejection(errors: FieldErrors, formErrors: List<String>, sent: Values) {
    val accepted = MutableStringMap<String>()
    for ((key, message) in errors) {
        val owner = core.visibility.owner(key)
        if (core.values.slot(owner).isSame(sent.slot(owner))) accepted[key] = message
    }
    core.applyValidationErrors(accepted, server = true, formErrors = formErrors)
}

internal suspend fun FormEngine.submitForm(submit: SubmitHandler, keepSkipped: Boolean = false, operationBox: Box<Int>? = null): Boolean {
    val core = core
    if (core.submitting || core.stepValidating) return false
    if (!keepSkipped) core.includeCurrentStep()
    var succeeded = false
    val epoch = core.validationEpoch
    val operation = core.beginOperation()
    operationBox?.value = operation
    val checked = core.getSubmitValues(payload = true)
    val inputsChanged = core.watchCheckedInputs(checked)
    val stale = { epoch != core.validationEpoch || operation != core.operationEpoch || inputsChanged() }
    core.submitting = true
    core.notify()
    try {
        val values = approveSubmission(checked, stale) ?: return false
        core.sending = operation
        val skippedSteps = core.skipped.map { it.id }
        val sent = core.values.copy()
        val serverErrors = core.serverErrorsGeneration
        if (keepSkipped) core.currentStep?.let { core.emit(FormEvent.StepSkipped(it.id)) }
        val result = send(submit, values, skippedSteps)
        if (operation != core.operationEpoch) return !result.isRejection
        if (!accept(result, sent, serverErrors)) return false
        succeeded = true
        suspended { afterCommit(FormLifecycleError.Phase.AFTER_SUBMIT) { options.onAfterSubmit?.invoke(values, skippedSteps) } }
        return true
    } catch (error: CancellationException) {
        return succeeded
    } catch (error: Throwable) {
        if (core.sending != operation && stale()) return false
        throw error
    } finally {
        if (operation == core.latestOperation) {
            core.submitting = false
            core.notify()
        }
    }
}

private fun FormEngine.accept(result: SubmitResult?, sent: Values, serverErrors: Int): Boolean {
    if (result is SubmitResult.Rejected) {
        applyRejection(result.errors, result.formErrors, sent)
        return false
    }
    if (core.serverErrorsGeneration == serverErrors) core.applyValidationErrors(MutableStringMap(), server = true)
    return true
}

private suspend fun FormEngine.approveSubmission(checked: JsonObject, stale: () -> Boolean): JsonObject? {
    if (stale() || invalid(stale)) return null
    val before = options.onBeforeSubmit ?: return checked
    val allowed = suspended { before(checked) }
    if (stale() || allowed == false || invalid(stale)) return null
    return core.getSubmitValues(payload = true)
}

private fun FormEngine.invalid(stale: () -> Boolean): Boolean {
    if (core.validate(navigate = true).isEmpty()) return stale()
    if (!stale()) core.emit(FormEvent.FormSubmitFailed(SubmitFailureReason.Validation))
    return true
}

private suspend fun FormEngine.send(submit: SubmitHandler, values: JsonObject, skippedSteps: List<String>): SubmitResult? {
    core.emit(FormEvent.FormSubmitted(values.size))
    val result = try {
        suspended { submit(values, skippedSteps) }
    } catch (error: Throwable) {
        core.emit(FormEvent.FormSubmitFailed(SubmitFailureReason.Error(submitStatus(error))))
        throw error
    }
    core.emit(if (result.isRejection) FormEvent.FormSubmitFailed(SubmitFailureReason.Rejected) else FormEvent.FormSubmitSucceeded)
    return result
}
