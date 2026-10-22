package dev.formhaus.core.engine

import dev.formhaus.core.definitionvalidation.DefinitionIssue
import dev.formhaus.core.json.FieldErrors
import dev.formhaus.core.json.JsonObject
import dev.formhaus.core.json.JsonValue
import dev.formhaus.core.json.MutableStringMap
import dev.formhaus.core.json.jsString
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

public data class StepChangeContext(
    public val fromStepId: String,
    public val toStepId: String,
    public val reason: StepChangeReason,
    public val values: JsonObject,
)

public sealed class SubmitResult {
    public data object Ok : SubmitResult()

    public data class Rejected(
        public val errors: FieldErrors = stringMapOf(),
        public val formErrors: List<String> = emptyList(),
    ) : SubmitResult()

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

public data class StepProgress(
    public val current: Int,
    public val total: Int,
)

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
) {
    internal val checksOn: Boolean get() = debugChecks == true
    internal val recallFormat: RecallFormatFn? by lazy { formatRecall ?: recallFormatter?.let(::recallFormat) }
}
