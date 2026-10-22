package dev.formhaus.core.engine

import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.partsHash
import java.io.IOException

public sealed class SubmitFailureReason(
    public val name: String,
) {
    public data object Validation : SubmitFailureReason("validation")

    public data object Rejected : SubmitFailureReason("rejected")

    public class Error(
        public val status: Int?,
    ) : SubmitFailureReason("error") {
        override fun equals(other: Any?): Boolean = equalParts(other, Error::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("Error")

        private fun parts(): ValueParts = listOf("status" to status)
    }
}

public interface SubmitStatusError {
    public val status: Int?
}

internal fun submitStatus(error: Throwable): Int? =
    when (error) {
        is SubmitStatusError -> error.status
        is IOException -> 0
        else -> null
    }

public enum class StepRedirectReason(
    public val rawValue: String,
) {
    BLOCKED("blocked"),
    NOT_ON_PATH("not-on-path"),
    UNKNOWN_STEP("unknown-step"),
}

public sealed class FormEvent(
    public val type: String,
) {
    public data object FormViewed : FormEvent("form_viewed")

    public class StepViewed(
        public val stepId: String,
        public val stepIndex: Int,
    ) : FormEvent("step_viewed") {
        override fun equals(other: Any?): Boolean = equalParts(other, StepViewed::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("StepViewed")

        private fun parts(): ValueParts = listOf("stepId" to stepId, "stepIndex" to stepIndex)
    }

    public class FieldError(
        public val fieldKey: String,
        public val error: String,
    ) : FormEvent("field_error") {
        override fun equals(other: Any?): Boolean = equalParts(other, FieldError::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("FieldError")

        private fun parts(): ValueParts = listOf("fieldKey" to fieldKey, "error" to error)
    }

    public class FieldShown(
        public val fieldKey: String,
    ) : FormEvent("field_shown") {
        override fun equals(other: Any?): Boolean = equalParts(other, FieldShown::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("FieldShown")

        private fun parts(): ValueParts = listOf("fieldKey" to fieldKey)
    }

    public class FieldHidden(
        public val fieldKey: String,
    ) : FormEvent("field_hidden") {
        override fun equals(other: Any?): Boolean = equalParts(other, FieldHidden::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("FieldHidden")

        private fun parts(): ValueParts = listOf("fieldKey" to fieldKey)
    }

    public class StepCompleted(
        public val stepId: String,
    ) : FormEvent("step_completed") {
        override fun equals(other: Any?): Boolean = equalParts(other, StepCompleted::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("StepCompleted")

        private fun parts(): ValueParts = listOf("stepId" to stepId)
    }

    public class StepSkipped(
        public val stepId: String,
    ) : FormEvent("step_skipped") {
        override fun equals(other: Any?): Boolean = equalParts(other, StepSkipped::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("StepSkipped")

        private fun parts(): ValueParts = listOf("stepId" to stepId)
    }

    public class FormSubmitted(
        public val fieldCount: Int,
    ) : FormEvent("form_submitted") {
        override fun equals(other: Any?): Boolean = equalParts(other, FormSubmitted::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("FormSubmitted")

        private fun parts(): ValueParts = listOf("fieldCount" to fieldCount)
    }

    public data object FormSubmitSucceeded : FormEvent("form_submit_succeeded")

    public class FormSubmitFailed(
        public val reason: SubmitFailureReason,
    ) : FormEvent("form_submit_failed") {
        override fun equals(other: Any?): Boolean = equalParts(other, FormSubmitFailed::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("FormSubmitFailed")

        private fun parts(): ValueParts = listOf("reason" to reason)
    }

    public class StepRedirected(
        public val requestedStepId: String,
        public val stepId: String?,
        public val reason: StepRedirectReason,
    ) : FormEvent("step_redirected") {
        override fun equals(other: Any?): Boolean = equalParts(other, StepRedirected::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("StepRedirected")

        private fun parts(): ValueParts = listOf("requestedStepId" to requestedStepId, "stepId" to stepId, "reason" to reason)
    }

    public data object FormReset : FormEvent("form_reset")
}
