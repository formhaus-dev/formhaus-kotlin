package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.i18n.LocaleCatalog
import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.json.ValueParts
import dev.formhaus.core.json.describe
import dev.formhaus.core.json.equalParts
import dev.formhaus.core.json.jsString
import dev.formhaus.core.json.partsHash
import dev.formhaus.core.json.stringMapOf
import dev.formhaus.core.model.FieldOption
import dev.formhaus.core.model.FormField
import dev.formhaus.core.validation.MessageSource
import dev.formhaus.core.validation.Validators

public enum class StepChangeReason(
    public val rawValue: String,
) {
    NEXT("next"),
    BACK("back"),
    AUTO_ADVANCE("autoAdvance"),
    SKIP("skip"),
}

public class StepChangeContext(
    public val fromStepId: String,
    public val toStepId: String,
    public val reason: StepChangeReason,
    public val values: JsonObject,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, StepChangeContext::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("StepChangeContext")

    private fun parts(): ValueParts = listOf("fromStepId" to fromStepId, "toStepId" to toStepId, "reason" to reason, "values" to values)
}

public class ValidateContext(
    public val stepId: String?,
    public val fields: List<String>,
    public val submit: Boolean,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, ValidateContext::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("ValidateContext")

    private fun parts(): ValueParts = listOf("stepId" to stepId, "fields" to fields, "submit" to submit)
}

public sealed class SubmitResult {
    public data object Ok : SubmitResult()

    public class Rejected(
        public val errors: FieldErrors = stringMapOf(),
        public val formErrors: List<String> = emptyList(),
    ) : SubmitResult() {
        override fun equals(other: Any?): Boolean = equalParts(other, Rejected::parts)

        override fun hashCode(): Int = parts().partsHash()

        override fun toString(): String = parts().describe("Rejected")

        private fun parts(): ValueParts = listOf("errors" to errors, "formErrors" to formErrors)
    }

    public companion object {
        public fun of(json: JsonValue?): SubmitResult? {
            val result = json?.objectValue ?: return null
            if (result["ok"] != JsonValue.Bool(false)) return Ok
            val errors = MutableStringMap<String>()
            for ((key, value) in result["errors"]?.objectValue ?: stringMapOf()) {
                val items = value.arrayValue
                val message =
                    items?.joinToString(" ") { if (it.isNull) "" else jsString(it) } ?: value.stringValue ?: continue
                errors[key] = message
            }
            return Rejected(errors, result["formErrors"]?.arrayValue?.mapNotNull { it.stringValue }.orEmpty())
        }
    }
}

public class StepProgress(
    public val current: Int,
    public val total: Int,
) {
    override fun equals(other: Any?): Boolean = equalParts(other, StepProgress::parts)

    override fun hashCode(): Int = parts().partsHash()

    override fun toString(): String = parts().describe("StepProgress")

    private fun parts(): ValueParts = listOf("current" to current, "total" to total)
}

public class GroupItem internal constructor(
    public val id: String,
    public val fields: List<FormField>,
) {
    override fun equals(other: Any?): Boolean =
        other is GroupItem && id == other.id && fields.map { it.ordinal } == other.fields.map { it.ordinal }

    override fun hashCode(): Int = 31 * id.hashCode() + fields.map { it.ordinal }.hashCode()
}

public class FormLifecycleError internal constructor(
    public val phase: Phase,
    cause: Throwable,
) : Exception("The action completed, but ${phase.rawValue} failed: ${errorMessage(cause)}", cause) {
    public enum class Phase(
        public val rawValue: String,
    ) {
        AFTER_STEP_CHANGE("afterStepChange"),
        AFTER_SUBMIT("afterSubmit"),
    }

    public val committed: Boolean = true
}

internal fun errorMessage(error: Throwable): String = error.message ?: error.toString()

public typealias StepValidateFn = suspend (String, JsonObject) -> FieldErrors?
public typealias ValidateFn = suspend (JsonObject, ValidateContext) -> FieldErrors?
public typealias BeforeStepChangeFn = suspend (StepChangeContext) -> Boolean?
public typealias AfterStepChangeFn = suspend (StepChangeContext) -> Unit
public typealias BeforeSubmitFn = suspend (JsonObject) -> Boolean?
public typealias AfterSubmitFn = suspend (JsonObject, List<String>) -> Unit
public typealias SubmitHandler = suspend (JsonObject, List<String>) -> SubmitResult?
public typealias FormEventFn = (FormEvent) -> Unit
public typealias WarningFn = (DefinitionIssue) -> Unit

public interface FormResearch {
    public fun seed(): String

    public fun order(
        seed: String,
        key: String,
        options: List<FieldOption>,
    ): List<FieldOption>
}

public enum class HiddenValues(
    public val rawValue: String,
) {
    CLEAR("clear"),
    KEEP("keep"),
    ;

    public companion object {
        public fun of(rawValue: String?): HiddenValues? = entries.firstOrNull { it.rawValue == rawValue }
    }
}

public class FormEngineOptions(
    public val onBeforeStepChange: BeforeStepChangeFn? = null,
    public val onAfterStepChange: AfterStepChangeFn? = null,
    public val onBeforeSubmit: BeforeSubmitFn? = null,
    public val onAfterSubmit: AfterSubmitFn? = null,
    public val onStepValidate: StepValidateFn? = null,
    public val onFormEvent: FormEventFn? = null,
    public val onWarning: WarningFn? = null,
    public val validators: Validators = stringMapOf(),
    public val messages: MessageSource? = null,
    public val lang: String? = null,
    public val formatRecall: RecallFormatFn? = null,
    public val recallFormatter: RecallFormatter? = null,
    public val seed: String? = null,
    public val research: FormResearch? = null,
    public val hiddenValues: HiddenValues = HiddenValues.CLEAR,
    public val debugChecks: Boolean? = null,
    public val locale: List<LocaleCatalog> = emptyList(),
    public val onValidate: ValidateFn? = null,
) {
    internal val checksOn: Boolean get() = debugChecks == true
    internal val recallFormat: RecallFormatFn? by lazy { formatRecall ?: recallFormatter?.let(::recallFormat) }
}
