package dev.formhaus.core.engine

import java.io.IOException

public sealed class SubmitFailureReason(public val name: String) {
    public data object Validation : SubmitFailureReason("validation")

    public data object Rejected : SubmitFailureReason("rejected")

    public data class Error(public val status: Int?) : SubmitFailureReason("error")
}

public interface SubmitStatusError {
    public val status: Int?
}

internal fun submitStatus(error: Throwable): Int? = when (error) {
    is SubmitStatusError -> error.status
    is IOException -> 0
    else -> null
}

public enum class StepRedirectReason(public val rawValue: String) {
    BLOCKED("blocked"),
    NOT_ON_PATH("not-on-path"),
    UNKNOWN_STEP("unknown-step"),
}

public sealed class FormEvent(public val type: String) {
    public data object FormViewed : FormEvent("form_viewed")

    public data class StepViewed(public val stepId: String, public val stepIndex: Int) : FormEvent("step_viewed")

    public data class FieldError(public val fieldKey: String, public val error: String) : FormEvent("field_error")

    public data class FieldShown(public val fieldKey: String) : FormEvent("field_shown")

    public data class FieldHidden(public val fieldKey: String) : FormEvent("field_hidden")

    public data class StepCompleted(public val stepId: String) : FormEvent("step_completed")

    public data class StepSkipped(public val stepId: String) : FormEvent("step_skipped")

    public data class FormSubmitted(public val fieldCount: Int) : FormEvent("form_submitted")

    public data object FormSubmitSucceeded : FormEvent("form_submit_succeeded")

    public data class FormSubmitFailed(public val reason: SubmitFailureReason) : FormEvent("form_submit_failed")

    public data class StepRedirected(
        public val requestedStepId: String,
        public val stepId: String?,
        public val reason: StepRedirectReason,
    ) : FormEvent("step_redirected")

    public data object FormReset : FormEvent("form_reset")
}
